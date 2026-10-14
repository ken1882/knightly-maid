package com.compeador.knightlymaid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidInfo;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * NeoForge treats an entity that only stops being tracked ('ServerLevel.EntityCallbacks#onTrackingEnd'), e.g. a rider carried into a chunk whose
 * entities are not loaded yet (a teleport, a fast dragon flight), like one leaving the level ('onRemovedFromLevel', {@link EntityLeaveLevelEvent}),
 * but not like one joining when tracking starts again ('onAddedToLevel', {@link net.neoforged.neoforge.event.entity.EntityJoinLevelEvent}).
 * Mods that pair the two lose track of such a maid, so this redoes their join handling once she is tracked again:
 * <ul>
 * <li>TLM records where a maid was last seen ('MaidWorldData#addInfo', listed by the red fox scroll) and clears it on join: one more stale
 * record each time.</li>
 * <li>Touhou Little Maid Spell drops her from its maid registry (listed by the Ender Pocket), releases her spell books and schedules her spell
 * data for removal, see {@link MaidSpellCompat#rejoin}.</li>
 * </ul>
 */
@EventBusSubscriber(modid = KnightlyMaid.MODID)
public class MaidRetrackFix {
    /** Touhou Little Maid Spell is optional: without it {@link MaidSpellCompat} is never loaded */
    static final boolean MAID_SPELL = ModList.get().isLoaded("touhou_little_maid_spell");

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Maids no longer tracked but not removed (server thread only); an entity is only equal to itself */
    private static final Set<EntityMaid> UNTRACKED_MAIDS = new HashSet<>();

    /** Also fired on removal; a removed maid (killed, unloaded with her chunk) is dropped below, her record is right then */
    @SubscribeEvent
    public static void rememberUntrackedMaid(final EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof EntityMaid maid && !maid.isRemoved()) {
            UNTRACKED_MAIDS.add(maid);
        }
    }

    /**
     * Cleans up records written before this fix. TLM clears all records of a maid whenever she is added or loaded ('onAddedToLevel'), so:
     * a maid that is tracked now has no real record, and of a maid that is not loaded only the newest record is real (written when she was
     * unloaded; an older one is from a moment she was untracked and came back). Records do not say what wrote them, so this is the only way
     * to tell them apart.
     */
    @SubscribeEvent
    public static void cleanUpOnLogin(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MaidWorldData data = MaidWorldData.get(player.level());
        // TLM's own list, changed in place
        List<MaidInfo> infos = data != null ? data.getPlayerMaidInfos(player) : null;

        if (infos == null || infos.isEmpty()) {
            return;
        }

        Map<UUID, MaidInfo> newest = new HashMap<>();
        infos.forEach(info -> newest.merge(info.getEntityId(), info, (kept, other) -> other.getTimestamp() > kept.getTimestamp() ? other : kept));
        int count = infos.size();
        infos.removeIf(info -> newest.get(info.getEntityId()) != info || isTracked(player.server, info.getEntityId()));

        if (infos.size() != count) {
            data.setDirty();
            LOGGER.info("Removed {} stale red fox scroll entries of {}", count - infos.size(), player.getScoreboardName());
        }
    }

    private static boolean isTracked(final MinecraftServer server, final UUID maidId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(maidId) instanceof EntityMaid) {
                return true;
            }
        }

        return false;
    }

    /** Per server tick: entity tick events are not fired for passengers ('ServerLevel#tickPassenger') */
    @SubscribeEvent
    public static void rejoinRetrackedMaids(final ServerTickEvent.Post event) {
        if (UNTRACKED_MAIDS.isEmpty()) {
            return;
        }

        UNTRACKED_MAIDS.removeIf(maid -> {
            if (maid.isRemoved()) {
                return true;
            }

            // Only tracked entities are found
            if (!(maid.level() instanceof ServerLevel level) || level.getEntity(maid.getUUID()) != maid) {
                return false;
            }

            MaidWorldData data = MaidWorldData.get(level);

            if (data != null) {
                data.removeInfo(maid);
            }

            if (MAID_SPELL) {
                MaidSpellCompat.rejoin(maid, level);
            }

            KnightlyMaidConfig.verbose(LOGGER, "Maid {} is tracked again at {}: cleared her stale last-seen records{}", maid.getUUID(),
                    maid.blockPosition().toShortString(), MAID_SPELL ? ", rejoined Touhou Little Maid Spell" : "");
            return true;
        });
    }
}
