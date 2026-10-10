package com.compeador.knightlymaid;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.function.BooleanSupplier;

@EventBusSubscriber(modid = KnightlyMaid.MODID)
public class MountMaidEvents {
    /** Same tip TLM shows for its saddle: "Right click again with the saddle in hand to eject the maid" (see 'ejectMaid') */
    static final String HOW_TO_EJECT = "message.touhou_little_maid.saddle.how_to_eject";

    /** Set (server thread only) while the owner moves the maid, so the riding guards let her switch vehicles */
    private static boolean ownerAction;

    static boolean runAsOwner(final BooleanSupplier action) {
        ownerAction = true;

        try {
            return action.getAsBoolean();
        } finally {
            ownerAction = false;
        }
    }

    static boolean isOwnerAction() {
        return ownerAction;
    }

    /** Runs before TLM's saddle handler, which would carry the maid in the player's arms */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void mountDragon(final InteractMaidEvent event) {
        Player player = event.getPlayer();
        EntityMaid maid = event.getMaid();

        if (!event.getStack().is(Items.SADDLE) || !player.getPassengers().isEmpty() || !maid.getPassengers().isEmpty() || !DragonSeat.canCarry(player)) {
            return;
        }

        event.setCanceled(true);

        if (!(player instanceof ServerPlayer dragon)) {
            return;
        }

        // Like TLM's saddle: otherwise she could not attack targets outside her home area
        if (maid.isHomeModeEnable()) {
            maid.setHomeModeEnable(false);
        }

        if (runAsOwner(() -> DragonSeat.mount(dragon, maid))) {
            dragon.displayClientMessage(Component.translatable(HOW_TO_EJECT), true);
        }
    }

    /**
     * The dragon cannot aim at its own passengers, so (like TLM's 'CancelSaddleMaidEvent') a saddle used in the air ejects the maid. <br>
     * TLM's handler only checks for a maid as the first passenger, which is the seat here.
     */
    @SubscribeEvent
    public static void ejectMaid(final PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();

        if (player.getFirstPassenger() instanceof DragonSeat && event.getItemStack().is(Items.SADDLE)) {
            player.ejectPassengers();
            event.setCanceled(true);
        }
    }

    /**
     * TLM lets a maid leave her vehicle on her own (e.g. panic when hurt or near hostiles), unless a player steers it. <br>
     * Nobody steers the seat, so without this she would jump off a flying dragon. On the ground TLM's behaviour is kept.
     */
    @SubscribeEvent
    public static void keepMaidSeated(final EntityMountEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntityMounting() instanceof EntityMaid maid) || !(maid.getVehicle() instanceof DragonSeat seat)) {
            return;
        }

        boolean isReleased = isOwnerAction() || seat.isReleasing() || !maid.isAlive() || seat.getVehicle() == null || seat.getVehicle().onGround();

        if (!isReleased) {
            // Also blocks mounting something else, since 'startRiding' would dismount her first
            event.setCanceled(true);
        } else if (event.isDismounting()) {
            maid.setData(KnightlyMaid.FALL_PROTECTION, true);
        }
    }

    /**
     * A riding maid cannot move away from blocks the dragon or horse carries her into (e.g. low ceilings, trees), so she does not suffocate. <br>
     * Vanilla horses also pass their fall damage on to their riders.
     */
    @SubscribeEvent
    public static void protectRidingMaid(final LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.getVehicle() instanceof DragonSeat || MaidMounts.isMaidMount(maid.getVehicle()))) {
            return;
        }

        if (event.getSource().is(DamageTypes.IN_WALL) || event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    /** A maid seated on the dragon when the game stopped without them logging out (e.g. a crash) is put down next to them */
    @SubscribeEvent
    public static void restoreSeatedMaid(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer dragon) {
            SeatedMaids.restore(dragon);
        }
    }

    /** A maid that left a dragon seat (in the air, e.g. ejected while flying or the dragon logged out) takes no damage when she lands */
    @SubscribeEvent
    public static void protectDroppedMaid(final LivingFallEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && maid.getData(KnightlyMaid.FALL_PROTECTION)) {
            maid.removeData(KnightlyMaid.FALL_PROTECTION);
            event.setCanceled(true);
        }
    }
}
