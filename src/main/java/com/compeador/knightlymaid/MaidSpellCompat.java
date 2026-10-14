package com.compeador.knightlymaid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.event.MaidSpellEventHandler;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * The only class that uses Touhou Little Maid Spell (an optional dependency). <br>
 * Only call it after checking {@link MaidRetrackFix#MAID_SPELL}, so its classes are never loaded without the mod.
 */
final class MaidSpellCompat {
    private MaidSpellCompat() { /* Nothing to do */ }

    /**
     * Runs Maid Spell's own join handling for a maid tracked again: back in its maid registry (listed by the Ender Pocket), spell books bound
     * again (which also cancels the removal of her spell data its leave handling scheduled in 100 ticks), step height. Safe to repeat. <br>
     * Only its handler is called, not a new {@link EntityJoinLevelEvent} for every mod.
     */
    static void rejoin(final EntityMaid maid, final ServerLevel level) {
        MaidSpellEventHandler.onEntityJoinLevel(new EntityJoinLevelEvent(maid, level));
    }
}
