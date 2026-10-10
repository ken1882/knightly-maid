package com.compeador.knightlymaid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import org.jetbrains.annotations.Nullable;

/**
 * Mounts a maid can ride on her own and drive herself (entity type tag {@code knightly_maid:maid_mounts}). <br>
 * Any mob can be tagged, but she only steers mobs that let a mob rider control them ({@link Mob#getControllingPassenger}), like horses.
 */
public final class MaidMounts {
    public static final TagKey<EntityType<?>> TAG = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(KnightlyMaid.MODID, "maid_mounts"));

    private MaidMounts() { /* Nothing to do */ }

    public static boolean isMaidMount(@Nullable final Entity entity) {
        // Trap skeleton horses summon lightning and skeleton riders once a player comes near
        return entity instanceof Mob mount && mount.getType().is(TAG) && !mount.isBaby() && !(mount instanceof SkeletonHorse skeleton && skeleton.isTrap());
    }

    /** She steers the mount when she is its first rider and it accepts a mob rider, see {@link Mob#getControllingPassenger} */
    public static boolean isDriving(final EntityMaid maid) {
        return isMaidMount(maid.getControlledVehicle());
    }

    /**
     * TLM's follow radius ('MaidNonHomeRange', 8 by default) sizes how far a maid on foot may stray from her owner. <br>
     * On a galloping horse that keeps 6+ blocks from its target, that is far too tight, so mounted maids use at least this.
     */
    private static final float MIN_MOUNTED_RADIUS = 16;

    /** Follow radius for a mounted maid: walks back to the owner beyond this - 2, teleported beyond this + 2 (TLM's formulas) */
    public static float followRadius(final EntityMaid maid) {
        return Math.max(maid.getRestrictRadius(), MIN_MOUNTED_RADIUS);
    }

    /**
     * Walk target speed modifier for the mount to move as fast as when a player rides it, unless set by {@link MountSettings#speedModifier}. <br>
     * AI movement uses the speed as both input and multiplier ({@link Mob#setSpeed} also sets 'zza'), so it moves at (modifier × speed)²,
     * while a player-ridden horse moves at about 0.98 (input) × speed.
     */
    public static float gallopSpeed(final LivingEntity maid) {
        if (maid.getControlledVehicle() instanceof LivingEntity mount) {
            MountSettings settings = MountSettings.get(mount);

            if (settings != null && settings.speedModifier().isPresent()) {
                return settings.speedModifier().get();
            }

            double speed = mount.getAttributeValue(Attributes.MOVEMENT_SPEED);

            if (speed > 0) {
                return (float) Math.sqrt(0.98 / speed);
            }
        }

        return 1;
    }
}
