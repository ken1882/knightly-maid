package com.compeador.knightlymaid;

import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

/**
 * Skeleton-like kiting on horseback for ranged modes: ride closer when out of range or sight, back off when too close, otherwise circle the target. <br>
 * Uses waypoints (the horse's pathfinding at gallop speed) instead of TLM's strafing tasks: those turn the maid rather than the horse,
 * strafe at a quarter of the speed, and only cover bows and crossbows.
 */
public class MountedKiteTask extends Behavior<EntityMaid> {
    /** Close-range spells need the maid near her target */
    private static final ResourceLocation SPELL_MELEE = ResourceLocation.fromNamespaceAndPath("maidspell", "spell_combat_melee");

    private static final double MIN_DISTANCE = 6;
    private static final double RETREAT_DISTANCE = MIN_DISTANCE + 4;
    private static final float CIRCLE_STEP = 25 * Mth.DEG_TO_RAD;
    private static final float[] RETREAT_ANGLES = {0, 45 * Mth.DEG_TO_RAD, -45 * Mth.DEG_TO_RAD, 90 * Mth.DEG_TO_RAD, -90 * Mth.DEG_TO_RAD, 135 * Mth.DEG_TO_RAD, -135 * Mth.DEG_TO_RAD};
    /** A new waypoint at most this often, so the horse does not re-path every tick */
    private static final int WAYPOINT_INTERVAL = 10;

    private boolean clockwise;
    private long nextDirectionSwitch;
    private long nextWaypoint;

    public MountedKiteTask() {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ));
    }

    public static boolean canKite(final EntityMaid maid) {
        return maid.getTask() instanceof IRangedAttackTask && !SPELL_MELEE.equals(maid.getTask().getUid());
    }

    @Override
    protected boolean checkExtraStartConditions(final ServerLevel level, final EntityMaid maid) {
        return level.getGameTime() >= nextWaypoint && maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).filter(LivingEntity::isAlive).isPresent();
    }

    @Override
    protected void start(final ServerLevel level, final EntityMaid maid, long gameTime) {
        LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);

        if (target == null || !(maid.getTask() instanceof IRangedAttackTask task)) {
            return;
        }

        nextWaypoint = gameTime + WAYPOINT_INTERVAL;
        BehaviorUtils.lookAtEntity(maid, target);
        float speed = MaidMounts.gallopSpeed(maid);

        // Out of the mode's range or line of sight (each mode's own check)
        if (!task.canSee(maid, target)) {
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, speed, (int) MIN_DISTANCE));
            return;
        }

        Vec3 away = new Vec3(maid.getX() - target.getX(), 0, maid.getZ() - target.getZ());
        double distance = away.length();
        Vec3 direction = distance > 1.0E-3 ? away.scale(1 / distance) : new Vec3(1, 0, 0);

        if (distance < MIN_DISTANCE) {
            // Straight away from the target first, then more and more to the side
            for (float angle : RETREAT_ANGLES) {
                if (tryWaypoint(maid, target.position().add(direction.yRot(angle).scale(RETREAT_DISTANCE)), speed)) {
                    return;
                }
            }
        } else {
            if (gameTime >= nextDirectionSwitch) {
                clockwise = maid.getRandom().nextBoolean();
                nextDirectionSwitch = gameTime + 20 + maid.getRandom().nextInt(21);
            }

            // At the edge of the leash: circle the other way
            for (boolean isClockwise : new boolean[]{clockwise, !clockwise}) {
                if (tryWaypoint(maid, target.position().add(direction.yRot(isClockwise ? -CIRCLE_STEP : CIRCLE_STEP).scale(distance)), speed)) {
                    clockwise = isClockwise;
                    return;
                }
            }
        }

        // Nowhere to go within the leash: ride back to the home center (home mode) or the owner instead of standing next to the target
        LivingEntity owner = maid.getOwner();

        if (maid.hasRestriction()) {
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(maid.getRestrictCenter(), speed, 3));
        } else if (owner != null && owner.level() == maid.level()) {
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(owner, speed, 3));
        }
    }

    private static boolean tryWaypoint(final EntityMaid maid, final Vec3 waypoint, float speed) {
        boolean isWithinLeash = isWithinLeash(maid, waypoint);

        if (isWithinLeash) {
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(waypoint, speed, 1));
        }

        return isWithinLeash;
    }

    /** The home area, or a little inside the (mounted) follow radius around the owner, like TLM's ranged walking */
    private static boolean isWithinLeash(final EntityMaid maid, final Vec3 waypoint) {
        if (maid.hasRestriction()) {
            return maid.isWithinRestriction(BlockPos.containing(waypoint));
        }

        LivingEntity owner = maid.getOwner();

        if (owner == null || owner.level() != maid.level()) {
            return true;
        }

        // Horizontal, so an owner flying or standing on a hill does not shrink it
        double radius = MaidMounts.followRadius(maid) - 2;
        double x = owner.getX() - waypoint.x;
        double z = owner.getZ() - waypoint.z;
        return x * x + z * z < radius * radius;
    }
}
