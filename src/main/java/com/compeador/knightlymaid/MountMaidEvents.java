package com.compeador.knightlymaid;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.api.mixin.IPlayerMixin;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

@EventBusSubscriber(modid = KnightlyMaid.MODID)
public class MountMaidEvents {
    /** Same tip TLM shows for its saddle: "Right click again with the saddle in hand to eject the maid" (see 'ejectMaid') */
    static final String HOW_TO_EJECT = "message.touhou_little_maid.saddle.how_to_eject";

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Farther than any seat position on a dragon; teleports shorter than the simulation distance are handled by vanilla anyway */
    private static final double LEFT_BEHIND_DISTANCE = 16;
    /** Maids seated on a dragon changing dimension, by dragon UUID (server thread only) */
    private static final Map<UUID, EntityMaid> TRAVELLING_MAIDS = new HashMap<>();
    /** Following maids of an owner changing dimension, by owner UUID (server thread only) */
    private static final Map<UUID, List<EntityMaid>> FOLLOWING_MAIDS = new HashMap<>();

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
            KnightlyMaidConfig.verbose(LOGGER, "{} ejects their seated maid with the saddle", player.getScoreboardName());
            player.ejectPassengers();
            event.setCanceled(true);
        }
    }

    /**
     * TLM lets a maid leave her vehicle on her own (e.g. panic when hurt or near hostiles), unless a player steers it. <br>
     * Nobody steers the seat, so without this she would jump off a flying dragon. On the ground TLM's behaviour is kept, except for
     * 'MaidFollowOwnerVehicleTask': for 3 s after the owner left a vehicle it makes maids near them leave theirs. TLM's 'PlayerMixin' marks every
     * 'Player#removeVehicle' call, also without a vehicle, so every teleport ('ServerPlayer#teleportTo' calls 'stopRiding') would throw her off.
     */
    @SubscribeEvent
    public static void keepMaidSeated(final EntityMountEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntityMounting() instanceof EntityMaid maid) || !(maid.getVehicle() instanceof DragonSeat seat)) {
            return;
        }

        boolean ownerLeftVehicle = maid.getOwner() instanceof IPlayerMixin owner && owner.tlmInRemoveVehicleCooldown();
        Entity carrier = seat.getVehicle();
        String release = isOwnerAction() ? "moved by owner" : seat.isReleasing() ? "seat released" : !maid.isAlive() ? "maid dead"
                : carrier == null ? "no carrier" : carrier.onGround() && !ownerLeftVehicle ? "carrier on the ground" : null;

        if (release == null) {
            // Also blocks mounting something else, since 'startRiding' would dismount her first
            event.setCanceled(true);
            KnightlyMaidConfig.verbose(LOGGER, "Kept maid {} on the seat of {}: blocked {} ({})", maid.getUUID(), carrier,
                    event.isDismounting() ? "dismount" : "mounting " + event.getEntityBeingMounted(), ownerLeftVehicle ? "owner left a vehicle within 3 s" : "carrier in the air");
        } else if (event.isDismounting()) {
            maid.setData(KnightlyMaid.FALL_PROTECTION, true);
            // On her own (TLM's AI or another mod): the stack trace shows who
            KnightlyMaidConfig.verbose(LOGGER, "Maid {} leaves the seat of {} ({})", maid.getUUID(), carrier, release,
                    carrier != null && carrier.onGround() && !seat.isReleasing() && !isOwnerAction() ? new Throwable("Dismounted by") : null);
        }
    }

    /**
     * A riding maid cannot move away from blocks the dragon or horse carries her into (e.g. low ceilings, trees), so she does not suffocate. <br>
     * Vanilla horses also pass their fall damage on to their riders. <br>
     * Dragon Survival's flight spin attacks everything around the dragon except direct passengers, which misses a maid on the seat.
     */
    @SubscribeEvent
    public static void protectRidingMaid(final LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.getVehicle() instanceof DragonSeat || MaidMounts.isMaidMount(maid.getVehicle()))) {
            return;
        }

        boolean isFromCarrier = maid.getVehicle() instanceof DragonSeat seat && seat.getVehicle() != null && event.getSource().getEntity() == seat.getVehicle();

        if (isFromCarrier || event.getSource().is(DamageTypes.IN_WALL) || event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
            KnightlyMaidConfig.verbose(LOGGER, "Blocked {} damage to maid {} riding {}", event.getSource().getMsgId(), maid.getUUID(), maid.getVehicle());
        }
    }

    /**
     * A passenger only moves with its vehicle while its own chunk is entity-ticking (players excepted, see 'ServerLevel#tickPassenger').
     * After a teleport beyond the simulation distance (e.g. waypoint mods, /tp) the seat would stay behind in a chunk that stops ticking,
     * and when that chunk unloads the maid is not saved (a passenger is only saved with her root vehicle). <br>
     * Runs before the levels tick, so before the old chunk can stop ticking or unload.
     */
    @SubscribeEvent
    public static void followTeleportedDragon(final ServerTickEvent.Pre event) {
        for (ServerPlayer dragon : event.getServer().getPlayerList().getPlayers()) {
            if (dragon.getFirstPassenger() instanceof DragonSeat seat && seat.distanceToSqr(dragon) > LEFT_BEHIND_DISTANCE * LEFT_BEHIND_DISTANCE) {
                KnightlyMaidConfig.verbose(LOGGER, "Seat of {} left behind at {} (teleport?), moving it to {}", dragon.getScoreboardName(),
                        seat.blockPosition().toShortString(), dragon.blockPosition().toShortString());
                dragon.positionRider(seat);
                seat.getPassengers().forEach(seat::positionRider);
            }
        }
    }

    /**
     * Vanilla drops a player's passengers when the player changes dimension (the seat puts her down in the old one). Remembered here,
     * after other mods could cancel the travel, so she can follow in {@link #followDragonToDimension}.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rememberTravellingMaid(final EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer dragon && dragon.level().dimension() != event.getDimension()
                && dragon.getFirstPassenger() instanceof DragonSeat seat && seat.getFirstPassenger() instanceof EntityMaid maid) {
            TRAVELLING_MAIDS.put(dragon.getUUID(), maid);
            KnightlyMaidConfig.verbose(LOGGER, "{} travels to {} with maid {}, she follows next tick", dragon.getScoreboardName(), event.getDimension().location(), maid.getUUID());
        }
    }

    /**
     * Moves her with 'Entity#teleportTo' like /tp does (her data is copied): TLM's 'EntityMaid#changeDimension' (portals) only teleports her
     * near her own spot. <br>
     * At the start of the next tick, so before her chunk in the old dimension can unload, and outside the call stack of the mod that
     * teleported the dragon: Touhou Little Maid Spell's anchor core wipes the data of a maid saved from a stack with classes it does not know.
     */
    @SubscribeEvent
    public static void followDragonToDimension(final ServerTickEvent.Pre event) {
        TRAVELLING_MAIDS.forEach((dragonId, maid) -> {
            ServerPlayer dragon = event.getServer().getPlayerList().getPlayer(dragonId);

            // E.g. the travel failed after the event, or the dragon logged out
            if (dragon == null || maid.isRemoved() || maid.isPassenger() || maid.level() == dragon.level()) {
                KnightlyMaidConfig.verbose(LOGGER, "Maid {} does not follow (dragon online: {}, maid removed: {}, riding: {}, same dimension: {})", maid.getUUID(),
                        dragon != null, maid.isRemoved(), maid.isPassenger(), dragon != null && maid.level() == dragon.level());
                return;
            }

            ServerLevel level = dragon.serverLevel();

            if (moveToLevel(maid, level, dragon.position(), dragon.getYRot()) instanceof EntityMaid movedMaid) {
                boolean seated = runAsOwner(() -> DragonSeat.mount(dragon, movedMaid));
                KnightlyMaidConfig.verbose(LOGGER, "Maid {} followed {} to {} (seated again: {})", movedMaid.getUUID(), dragon.getScoreboardName(), level.dimension().location(), seated);
            } else {
                LOGGER.warn("Maid {} could not follow {} to {}", maid.getUUID(), dragon.getScoreboardName(), level.dimension().location());
            }
        });

        TRAVELLING_MAIDS.clear();
    }

    /**
     * TLM's maids follow their owner only within a dimension ('MaidFollowOwnerTask' and its teleport before a chunk unloads), and cannot use
     * portals ('EntityMaid#changeDimension'). So the owner's following maids are remembered here, after other mods could cancel the travel,
     * and moved in {@link #followOwnerToDimension}.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rememberFollowingMaids(final EntityTravelToDimensionEvent event) {
        if (!KnightlyMaidConfig.followAcrossDimensions() || !(event.getEntity() instanceof ServerPlayer owner) || owner.level().dimension() == event.getDimension()) {
            return;
        }

        List<EntityMaid> maids = new ArrayList<>();

        for (Entity entity : owner.serverLevel().getAllEntities()) {
            if (entity instanceof EntityMaid maid && maid.isOwnedBy(owner) && isFollowing(maid, owner)) {
                maids.add(maid);
            }
        }

        if (!maids.isEmpty()) {
            FOLLOWING_MAIDS.put(owner.getUUID(), maids);
            KnightlyMaidConfig.verbose(LOGGER, "{} travels to {}, following maids follow next tick: {}", owner.getScoreboardName(), event.getDimension().location(),
                    maids.stream().map(EntityMaid::getUUID).toList());
        }
    }

    /**
     * Following like TLM's 'MaidFollowOwnerTask' (on foot: 'canBrainMoving', so not sitting, sleeping, leashed or riding) or
     * {@link DriveMountToOwnerTask} (driving her mount), or carried by the owner (TLM's saddle; vanilla puts her down). <br>
     * A maid on a dragon seat is moved by {@link #followDragonToDimension}.
     */
    private static boolean isFollowing(final EntityMaid maid, final Player owner) {
        return maid.isAlive() && !maid.isHomeModeEnable() && (maid.canBrainMoving() || MaidMounts.isDriving(maid) || maid.getVehicle() == owner);
    }

    /** Same timing as {@link #followDragonToDimension}, for the same reasons */
    @SubscribeEvent
    public static void followOwnerToDimension(final ServerTickEvent.Pre event) {
        FOLLOWING_MAIDS.forEach((ownerId, maids) -> {
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(ownerId);

            for (EntityMaid maid : maids) {
                // E.g. the travel failed after the event, or the owner logged out
                if (owner == null || maid.isRemoved() || maid.level() == owner.level()) {
                    KnightlyMaidConfig.verbose(LOGGER, "Maid {} does not follow (owner online: {}, maid removed: {}, same dimension: {})", maid.getUUID(),
                            owner != null, maid.isRemoved(), owner != null && maid.level() == owner.level());
                    continue;
                }

                Entity mount = MaidMounts.isDriving(maid) ? maid.getVehicle() : null;
                boolean followed = mount != null ? runAsOwner(() -> moveWithMount(maid, mount, owner)) : moveOnFoot(maid, owner);

                if (followed) {
                    KnightlyMaidConfig.verbose(LOGGER, "Maid {} followed {} to {} (on {})", maid.getUUID(), owner.getScoreboardName(), owner.level().dimension().location(),
                            mount != null ? mount.getType().toShortString() : "foot");
                } else {
                    LOGGER.warn("Maid {} could not follow {} to {}", maid.getUUID(), owner.getScoreboardName(), owner.level().dimension().location());
                }
            }
        });

        FOLLOWING_MAIDS.clear();
    }

    private static boolean moveOnFoot(final EntityMaid maid, final ServerPlayer owner) {
        if (!(moveToLevel(maid, owner.serverLevel(), owner.position(), owner.getYRot()) instanceof EntityMaid moved)) {
            return false;
        }

        // TLM's follow teleport: a free spot near the owner, so several maids do not stand on one block
        moved.teleportToOwner(owner);
        return true;
    }

    /** The mount first (which takes her off, allowed as owner action), then her, seated again and placed like {@link DriveMountToOwnerTask} does */
    private static boolean moveWithMount(final EntityMaid maid, final Entity mount, final ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();

        if (!(moveToLevel(mount, level, owner.position(), owner.getYRot()) instanceof Mob movedMount)
                || !(moveToLevel(maid, level, owner.position(), owner.getYRot()) instanceof EntityMaid moved)) {
            return false;
        }

        moved.startRiding(movedMount);
        DriveMountToOwnerTask.teleportNear(movedMount, owner.blockPosition());
        return moved.getVehicle() == movedMount;
    }

    /**
     * The steps of 'Entity#teleportTo' to another level, which gives no access to the copy and whether the new level accepted it (a mod can
     * cancel 'EntityJoinLevelEvent'). The original is still removed first, as there: Touhou Little Maid Spell clears its state of a maid's
     * UUID when she leaves a level, which would hit the copy if it was added first. If the copy is refused the entity is put back where it was.
     *
     * @return its copy in the new level, or {@code null} if it could not be moved
     */
    private static @Nullable Entity moveToLevel(final Entity entity, final ServerLevel level, final Vec3 position, float yRot) {
        ServerLevel oldLevel = (ServerLevel) entity.level();
        Entity copy = entity.getType().create(level);

        if (copy == null) {
            return null;
        }

        entity.unRide();
        copy.restoreFrom(entity);
        copy.moveTo(position.x, position.y, position.z, yRot, 0);
        entity.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);

        if (level.addFreshEntity(copy)) {
            return copy;
        }

        Entity restored = entity.getType().create(oldLevel);

        if (restored != null) {
            restored.restoreFrom(entity);
            restored.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());

            if (oldLevel.addFreshEntity(restored)) {
                LOGGER.warn("{} refused {} {}, put it back in {}", level.dimension().location(), entity.getType().toShortString(), entity.getUUID(), oldLevel.dimension().location());
                return null;
            }
        }

        // Last resort: its data, to restore it by hand with /summon
        LOGGER.error("{} and {} refused {} {}, its data: {}", level.dimension().location(), oldLevel.dimension().location(), EntityType.getKey(entity.getType()),
                entity.getUUID(), entity.saveWithoutId(new CompoundTag()));
        return null;
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
            KnightlyMaidConfig.verbose(LOGGER, "Blocked fall damage of maid {} that left a seat", maid.getUUID());
        }
    }
}
