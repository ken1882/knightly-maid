package com.compeador.knightlymaid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * TLM's 'MaidFollowOwnerTask' for a maid driving her horse (TLM's version does not run while she rides). <br>
 * Her walk target moves the horse, since a mob steering a mount uses the mount's navigation ({@link Mob#getNavigation}).
 */
public class DriveMountToOwnerTask extends Behavior<EntityMaid> {
    /** Wider than TLM's 2 blocks, for the horse's size */
    private static final int STOP_DISTANCE = 3;

    public DriveMountToOwnerTask() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED));
    }

    @Override
    protected boolean checkExtraStartConditions(final ServerLevel level, final EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        return MaidMounts.isDriving(maid) && !maid.isHomeModeEnable() && owner != null && owner.isAlive() && !owner.isSpectator() && owner.level() == level;
    }

    @Override
    protected void start(final ServerLevel level, final EntityMaid maid, long gameTime) {
        LivingEntity owner = maid.getOwner();

        if (owner == null || !(maid.getControlledVehicle() instanceof Mob mount)) {
            return;
        }

        // TLM's 'MaidFollowOwnerTask' formulas, with the larger mounted radius
        int startDistance = (int) MaidMounts.followRadius(maid) - 2;
        int teleportDistance = startDistance + 4;

        if (maid.closerThan(owner, startDistance)) {
            return;
        }

        if (!maid.closerThan(owner, teleportDistance)) {
            if (teleportNear(mount, owner.blockPosition())) {
                mount.getNavigation().stop();
                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            }
        } else if (!isWalkTarget(maid, owner)) {
            BehaviorUtils.setWalkAndLookTargetMemories(maid, owner, MaidMounts.gallopSpeed(maid), STOP_DISTANCE);
        }
    }

    private static boolean isWalkTarget(final EntityMaid maid, final LivingEntity owner) {
        return maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(target -> target.getTarget() instanceof EntityTracker tracker && tracker.getEntity().equals(owner))
                .orElse(false);
    }

    /** Same as the (private) 'TamableAnimal#teleportToAroundBlockPos'. The rider moves along with the mount. */
    private static boolean teleportNear(final Mob mount, final BlockPos target) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = mount.getRandom().nextIntBetweenInclusive(-3, 3);
            int z = mount.getRandom().nextIntBetweenInclusive(-3, 3);

            if (Math.abs(x) < 2 && Math.abs(z) < 2) {
                continue;
            }

            BlockPos position = target.offset(x, mount.getRandom().nextIntBetweenInclusive(-1, 1), z);

            if (canTeleportTo(mount, position)) {
                mount.teleportTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
                return true;
            }
        }

        return false;
    }

    /** Same as the (private) 'TamableAnimal#canTeleportTo' */
    private static boolean canTeleportTo(final Mob mount, final BlockPos position) {
        if (WalkNodeEvaluator.getPathTypeStatic(mount, position) != PathType.WALKABLE) {
            return false;
        }

        if (mount.level().getBlockState(position.below()).getBlock() instanceof LeavesBlock) {
            return false;
        }

        return mount.level().noCollision(mount, mount.getBoundingBox().move(position.subtract(mount.blockPosition())));
    }
}
