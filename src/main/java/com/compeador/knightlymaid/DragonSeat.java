package com.compeador.knightlymaid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Invisible seat between a carrier and a maid (carrier ← seat ← maid). The carrier is a dragon player, or any entity in the tag
 * {@code knightly_maid:maid_carriers}. <br>
 * TLM carries a maid in its arms whenever her vehicle is a player; with the seat in between she rides a normal vehicle instead.
 * On other entities the seat keeps her from becoming their controlling passenger, and places her ({@link MountSettings#seatOffset}).
 */
public class DragonSeat extends Entity {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Dragon Survival is optional: without it {@link DragonCompat} is never loaded */
    static final boolean DRAGON_SURVIVAL = ModList.get().isLoaded("dragonsurvival");

    /** Entities that carry a maid on a seat. Players in it carry her on their back whether or not they are dragons. */
    public static final TagKey<EntityType<?>> CARRIERS = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(KnightlyMaid.MODID, "maid_carriers"));

    /** Set while the seat itself drops the maid, see {@link MountMaidEvents#keepMaidSeated} */
    private boolean releasing;

    public DragonSeat(final EntityType<?> type, final Level level) {
        super(type, level);
    }

    public boolean isReleasing() {
        return releasing || isRemoved();
    }

    /** Only uses state known to both sides, so the client predicts the same saddle clicks as the server */
    public static boolean canCarry(@Nullable final Entity carrier) {
        if (carrier == null || !carrier.isAlive()) {
            return false;
        }

        return carrier.getType().is(CARRIERS) || carrier instanceof Player player && isRideableDragon(player);
    }

    private static boolean isRideableDragon(final Player player) {
        return DRAGON_SURVIVAL && DragonCompat.isRideableDragon(player);
    }

    /** @return whether the maid is now seated on the carrier */
    public static boolean mount(final Entity carrier, final EntityMaid maid) {
        DragonSeat seat = new DragonSeat(KnightlyMaid.DRAGON_SEAT.get(), carrier.level());
        seat.setPos(carrier.position());

        if (!carrier.level().addFreshEntity(seat)) {
            KnightlyMaidConfig.verbose(LOGGER, "Could not seat maid {} on {}: seat not added to the level", maid.getUUID(), carrier);
            return false;
        }

        if (!seat.startRiding(carrier) || !maid.startRiding(seat)) {
            KnightlyMaidConfig.verbose(LOGGER, "Could not seat maid {} on {}: riding refused (seat on carrier: {})", maid.getUUID(), carrier, seat.getVehicle() == carrier);
            seat.discard();
            return false;
        }

        KnightlyMaidConfig.verbose(LOGGER, "Seated maid {} on {}", maid.getUUID(), carrier);

        if (carrier instanceof ServerPlayer player) {
            // The tracker does not send a player's own passengers to that player
            player.connection.send(new ClientboundSetPassengersPacket(player));
            // A passenger of a player is in no save of her own
            SeatedMaids.create(player.server);
        }

        return true;
    }

    @Override
    public void tick() {
        super.tick();

        if (getVehicle() != null) {
            setYRot(getBodyYaw(getVehicle()));
        }

        if (!level().isClientSide() && !(canCarry(getVehicle()) && getFirstPassenger() instanceof EntityMaid)) {
            KnightlyMaidConfig.verbose(LOGGER, "Seat on {} is no longer used (carrier can carry: {}, passenger: {})", getVehicle(), canCarry(getVehicle()), getFirstPassenger());

            if (getVehicle() != null) {
                stopRiding();
            } else {
                // E.g. loaded without its carrier
                ejectPassengers();
                discard();
            }
        }
    }

    /** Applies {@link MountSettings#seatOffset} after the carrier placed the seat, so it also works with carriers that place passengers their own way */
    @Override
    public void rideTick() {
        super.rideTick();
        Entity vehicle = getVehicle();

        if (vehicle == null || getMountingBoneOffset(vehicle) != null) {
            return;
        }

        MountSettings settings = MountSettings.get(vehicle);

        if (settings != null && settings.seatOffset().isPresent()) {
            setPos(vehicle.position().add(settings.seatOffset().get().yRot(-getBodyYaw(vehicle) * Mth.DEG_TO_RAD)));
        }
    }

    private static float getBodyYaw(final Entity carrier) {
        if (carrier instanceof Player player && isRideableDragon(player)) {
            return DragonCompat.getBodyYaw(player);
        }

        return carrier instanceof LivingEntity living ? living.yBodyRot : carrier.getYRot();
    }

    /** Also called when the carrier logs out, dies or changes dimension. Dropping the maid right away keeps her saved in the world. */
    @Override
    public void removeVehicle() {
        // 'discard' below calls 'setRemoved', which calls 'stopRiding' again
        if (level().isClientSide() || isRemoved()) {
            super.removeVehicle();
            return;
        }

        Entity vehicle = getVehicle();

        if (vehicle != null) {
            KnightlyMaidConfig.verbose(LOGGER, "Seat leaves {} (carrier removal: {}), putting down {}", vehicle, vehicle.getRemovalReason(), getPassengers());
        }

        // While still riding, so the maid is placed next to the carrier instead of falling from its back
        releasing = true;
        ejectPassengers();
        super.removeVehicle();

        if (getVehicle() == null) {
            discard();

            if (vehicle instanceof ServerPlayer dragon) {
                dragon.connection.send(new ClientboundSetPassengersPacket(dragon));
            }
        }
    }

    /** On a player she is saved by {@link SeatedMaids}: a player's passengers are never loaded back. Other carriers save the seat and her with them. */
    @Override
    public boolean saveAsPassenger(final CompoundTag tag) {
        return !(getVehicle() instanceof Player) && super.saveAsPassenger(tag);
    }

    /** Vanilla passes a vehicle's fall damage on to its passengers, so every landing of the dragon (e.g. after a jump) would hurt the maid */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, final DamageSource source) {
        return false;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(final LivingEntity passenger) {
        return getVehicle() != null ? getVehicle().getDismountLocationForPassenger(passenger) : super.getDismountLocationForPassenger(passenger);
    }

    /**
     * YSM models are made for players, which sit {@link Player#DEFAULT_VEHICLE_ATTACHMENT} lower than other passengers (a maid has no
     * vehicle attachment). TLM hands YSM the whole rendering of a maid, and on a vehicle it does not know (like this seat) YSM plays the
     * player 'sit' animation without lowering her, so she would float above the seat by that much. <br>
     * Not in TLM's sitting pose (waiting): YSM's maid renderer already lowers her then, and skips its vehicle offsets.
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(final Entity passenger, final EntityDimensions dimensions, float scale) {
        Vec3 point = super.getPassengerAttachmentPoint(passenger, dimensions, scale);

        if (passenger instanceof EntityMaid maid && maid.isYsmModel() && !maid.isMaidInSittingPose()) {
            return point.subtract(Player.DEFAULT_VEHICLE_ATTACHMENT.scale(maid.getScale()));
        }

        return point;
    }

    /** Places the seat on the dragon's mounting bone (where DS seats player riders) */
    @Override
    public Vec3 getVehicleAttachmentPoint(final Entity vehicle) {
        Vec3 offset = getMountingBoneOffset(vehicle);

        if (offset == null) {
            return super.getVehicleAttachmentPoint(vehicle);
        }

        // 'positionRider' places us at 'getPassengerRidingPosition(this)' minus this point
        return vehicle.getPassengerRidingPosition(this).subtract(vehicle.position().add(offset));
    }

    /** Only known on the client: DS syncs it to the server for player riders only, so the server uses the vanilla seat position */
    private @Nullable Vec3 getMountingBoneOffset(final Entity vehicle) {
        if (!level().isClientSide() || !(vehicle instanceof Player dragon) || !isRideableDragon(dragon)) {
            return null;
        }

        return DragonCompat.getMountingBoneOffset(dragon);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) { /* Nothing to do */ }

    /** Nothing of its own: the carrier saves it only for its passenger, see {@link #saveAsPassenger} */
    @Override
    protected void readAdditionalSaveData(final CompoundTag tag) { /* Nothing to do */ }

    @Override
    protected void addAdditionalSaveData(final CompoundTag tag) { /* Nothing to do */ }
}
