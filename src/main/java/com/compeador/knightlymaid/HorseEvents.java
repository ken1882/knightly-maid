package com.compeador.knightlymaid;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Moving a maid with the saddle between the player (TLM's carry or a dragon seat) and a mount she drives (tag {@code knightly_maid:maid_mounts})
 * or a carrier she sits on (tag {@code knightly_maid:maid_carriers}), and keeping her on her mount
 */
@EventBusSubscriber(modid = KnightlyMaid.MODID)
public class HorseEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TRANSFER_FAILED = "message.knightly_maid.transfer_failed";

    /** @param mount the mount or carrier to put the maid on, or {@code null} to take her back from it */
    private record Transfer(EntityMaid maid, @Nullable Entity mount) { }

    /** Saddle used on a mount or carrier: put the carried maid on it, or take back the maid riding it */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void transferWithSaddle(final PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();

        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().is(Items.SADDLE)) {
            return;
        }

        // Nothing to transfer: keep the vanilla interaction (e.g. saddling or riding the horse)
        Transfer transfer = find(player, event.getTarget());

        if (transfer == null) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (player instanceof ServerPlayer serverPlayer) {
            boolean isSuccess = transfer.mount() != null ? putOn(serverPlayer, transfer.maid(), transfer.mount()) : takeBack(serverPlayer, transfer.maid());

            if (!isSuccess) {
                fail(serverPlayer, transfer.maid());
            }
        }
    }

    /**
     * Clicks on a maid riding her mount or a carrier. TLM's own saddle and dismount handlers would be blocked by {@link #keepMaidOnMount}
     * and {@link MountMaidEvents#keepMaidSeated}.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interactRidingMaid(final InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();

        if (!MaidMounts.isMaidMount(maid.getVehicle()) && !isOnCarrier(maid)) {
            return;
        }

        if (event.getStack().is(Items.SADDLE)) {
            // Dragons are handled by 'MountMaidEvents#mountDragon'
            if (!player.getPassengers().isEmpty() || DragonSeat.canCarry(player)) {
                return;
            }

            event.setCanceled(true);

            if (player instanceof ServerPlayer serverPlayer && !takeBack(serverPlayer, maid)) {
                fail(serverPlayer, maid);
            }
        } else if (player.isShiftKeyDown() && !player.getMainHandItem().is(InitItems.KAPPA_COMPASS.get())) {
            // Same as TLM's 'DismountMaidEvent'
            event.setCanceled(true);

            if (player instanceof ServerPlayer serverPlayer && !MountMaidEvents.runAsOwner(() -> {
                maid.stopRiding();
                return !maid.isPassenger();
            })) {
                fail(serverPlayer, maid);
            }
        }
    }

    /**
     * TLM makes a maid leave or switch her vehicle on her own (e.g. when the owner leaves a vehicle, panic, schedule). <br>
     * On her own mount only the owner moves her. Cancelling the mount stops 'startRiding' before it dismounts her.
     */
    @SubscribeEvent
    public static void keepMaidOnMount(final EntityMountEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntityMounting() instanceof EntityMaid maid) || !MaidMounts.isMaidMount(maid.getVehicle())) {
            return;
        }

        // Removal or death (also chunk unloading), and vanilla's underwater dismount so she can surface instead of drowning
        boolean isAllowed = MountMaidEvents.isOwnerAction() || !maid.isAlive() || !maid.getVehicle().isAlive() || maid.isEyeInFluid(FluidTags.WATER);

        if (!isAllowed) {
            event.setCanceled(true);
        }
    }

    /**
     * A horse rears for a second on 1 in 3 hits ({@link AbstractHorse#hurt}) and cannot move meanwhile ({@link AbstractHorse#isImmobile}),
     * so in a fight her horse would keep stalling. Cleared before the horse's tick, so no movement is lost.
     */
    @SubscribeEvent
    public static void preventRearing(final EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof AbstractHorse horse && horse.isStanding() && !horse.level().isClientSide()
                && horse.getControllingPassenger() instanceof EntityMaid && MaidMounts.isMaidMount(horse)) {
            horse.setStanding(false);
        }
    }

    /** Only uses state known to both sides, so the client cancels the same clicks as the server */
    private static @Nullable Transfer find(final Player player, final Entity target) {
        boolean isMount = MaidMounts.isMaidMount(target);

        // Players carry her through the saddle on the maid herself, see 'MountMaidEvents#mountDragon'
        if (!isMount && (target instanceof Player || !DragonSeat.canCarry(target))) {
            return null;
        }

        EntityMaid carried = getCarriedMaid(player);

        if (carried != null) {
            // Whether a carrier has room is up to it ('canAddPassenger'), checked when seating her
            return carried.isOwnedBy(player) && (!isMount || target.getPassengers().isEmpty()) ? new Transfer(carried, target) : null;
        }

        if (!player.getPassengers().isEmpty()) {
            return null;
        }

        for (Entity passenger : target.getPassengers()) {
            if (passenger instanceof DragonSeat seat) {
                passenger = seat.getFirstPassenger();
            }

            if (passenger instanceof EntityMaid maid && maid.isOwnedBy(player)) {
                return new Transfer(maid, null);
            }
        }

        return null;
    }

    /** Seated on a carrier other than a player */
    private static boolean isOnCarrier(final EntityMaid maid) {
        return maid.getVehicle() instanceof DragonSeat seat && !(seat.getVehicle() instanceof Player);
    }

    /** Carried by TLM's saddle (riding the player) or seated on the dragon */
    private static @Nullable EntityMaid getCarriedMaid(final Player player) {
        Entity passenger = player.getFirstPassenger();

        if (passenger instanceof DragonSeat seat) {
            passenger = seat.getFirstPassenger();
        }

        return passenger instanceof EntityMaid maid ? maid : null;
    }

    private static boolean putOn(final ServerPlayer player, final EntityMaid maid, final Entity mount) {
        boolean wasCarriedByPlayer = maid.getVehicle() == player;

        boolean isSuccess = MaidMounts.isMaidMount(mount) ? MountMaidEvents.runAsOwner(() -> maid.startRiding(mount))
                : MountMaidEvents.runAsOwner(() -> DragonSeat.mount(mount, maid));

        if (!isSuccess) {
            return false;
        }

        // The tracker does not send a player's own passengers to that player (an empty dragon seat removes itself)
        if (wasCarriedByPlayer) {
            player.connection.send(new ClientboundSetPassengersPacket(player));
        }

        return true;
    }

    private static boolean takeBack(final ServerPlayer player, final EntityMaid maid) {
        boolean isSuccess;

        if (DragonSeat.canCarry(player)) {
            isSuccess = MountMaidEvents.runAsOwner(() -> DragonSeat.mount(player, maid));
        } else {
            isSuccess = MountMaidEvents.runAsOwner(() -> maid.startRiding(player));

            if (isSuccess) {
                player.connection.send(new ClientboundSetPassengersPacket(player));
            }
        }

        if (isSuccess) {
            // Like TLM's saddle: otherwise she could not attack targets outside her home area
            if (maid.isHomeModeEnable()) {
                maid.setHomeModeEnable(false);
            }

            player.displayClientMessage(Component.translatable(MountMaidEvents.HOW_TO_EJECT), true);
        }

        return isSuccess;
    }

    private static void fail(final ServerPlayer player, final EntityMaid maid) {
        player.displayClientMessage(Component.translatable(TRANSFER_FAILED), true);
        LOGGER.debug("Could not move maid {} (vehicle: {}) for player {}", maid, maid.getVehicle(), player.getScoreboardName());
    }
}
