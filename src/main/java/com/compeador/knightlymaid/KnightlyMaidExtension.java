package com.compeador.knightlymaid;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRangedWalkToTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;

import java.util.List;

/** Found by TLM through the annotation. The behaviour lists are requested per maid, so each brain gets its own instances. */
@LittleMaidExtension
public class KnightlyMaidExtension implements ILittleMaid {
    @Override
    public void addExtraMaidBrain(final ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new IExtraMaidBrain() {
            /** Core behaviours run in every activity, including TLM's riding ones */
            @Override
            public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> getCoreBehaviors() {
                // Same priority as TLM's follow owner task
                return List.of(Pair.of(3, new DriveMountToOwnerTask()));
            }

            /** TLM's attack tasks leave out moving to the target while riding: melee and close-range spells use TLM's on-foot walk tasks, other ranged modes kite. All at the mount's gallop. */
            @Override
            public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> getRideWorkBehaviors() {
                return List.of(
                        Pair.of(5, new WhileDriving(maid -> !(maid.getTask() instanceof IRangedAttackTask), SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(MaidMounts::gallopSpeed))),
                        Pair.of(5, new WhileDriving(MountedKiteTask::canKite, new MountedKiteTask())),
                        // Ranged modes that do not kite (close-range spells)
                        Pair.of(5, new WhileDriving(maid -> maid.getTask() instanceof IRangedAttackTask && !MountedKiteTask.canKite(maid), MaidRangedWalkToTarget.create(MaidMounts::gallopSpeed)))
                );
            }
        });
    }
}
