package com.compeador.knightlymaid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.function.Predicate;

/** Starts the wrapped behaviour only while the maid drives her mount (and the extra condition holds) */
public record WhileDriving(Predicate<EntityMaid> condition, BehaviorControl<? super EntityMaid> behavior) implements BehaviorControl<EntityMaid> {
    @Override
    public Behavior.Status getStatus() {
        return behavior.getStatus();
    }

    @Override
    public boolean tryStart(final ServerLevel level, final EntityMaid maid, long gameTime) {
        return MaidMounts.isDriving(maid) && condition.test(maid) && behavior.tryStart(level, maid, gameTime);
    }

    @Override
    public void tickOrStop(final ServerLevel level, final EntityMaid maid, long gameTime) {
        behavior.tickOrStop(level, maid, gameTime);
    }

    @Override
    public void doStop(final ServerLevel level, final EntityMaid maid, long gameTime) {
        behavior.doStop(level, maid, gameTime);
    }

    @Override
    public String debugString() {
        return behavior.debugString();
    }
}
