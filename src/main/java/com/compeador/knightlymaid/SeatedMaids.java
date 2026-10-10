package com.compeador.knightlymaid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Saves the maids seated on dragons, so they are not lost when the game stops without the dragon logging out (crash, killed process). <br>
 * A passenger is only saved with her root vehicle, and a player's passengers are never loaded back (vanilla only loads the vehicle a player
 * rides), so otherwise a seated maid is in no save at all. <br>
 * Written while the overworld saves, in the same pass as the chunks, so it matches the rest of the world. Also keeps to the vanilla call
 * stacks that Touhou Little Maid Spell's anchor core allows to save a maid. On the dragon's next login she is put down next to them, as if
 * they had logged out normally.
 */
public class SeatedMaids extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = KnightlyMaid.MODID + "_seated_maids";

    private final MinecraftServer server;
    /** Maids seated when the game last saved, of dragons that have not logged in since, by dragon UUID */
    private final Map<UUID, CompoundTag> unrestored = new HashMap<>();

    private SeatedMaids(final MinecraftServer server) {
        this.server = server;
    }

    private static Factory<SeatedMaids> factory(final MinecraftServer server) {
        return new Factory<>(() -> new SeatedMaids(server), (tag, provider) -> load(server, tag));
    }

    /** Called when a maid is seated, so the save exists from then on */
    static void create(final MinecraftServer server) {
        server.overworld().getDataStorage().computeIfAbsent(factory(server), NAME);
    }

    private static @Nullable SeatedMaids get(final MinecraftServer server) {
        return server.overworld().getDataStorage().get(factory(server), NAME);
    }

    private static SeatedMaids load(final MinecraftServer server, final CompoundTag tag) {
        SeatedMaids seatedMaids = new SeatedMaids(server);

        for (String dragon : tag.getAllKeys()) {
            seatedMaids.unrestored.put(UUID.fromString(dragon), tag.getCompound(dragon));
        }

        return seatedMaids;
    }

    /** Puts down the maid that was seated on this dragon when the game last saved */
    static void restore(final ServerPlayer dragon) {
        SeatedMaids seatedMaids = get(dragon.server);
        CompoundTag tag = seatedMaids != null ? seatedMaids.unrestored.remove(dragon.getUUID()) : null;

        if (tag == null) {
            return;
        }

        ServerLevel level = dragon.serverLevel();

        if (!(EntityType.create(tag, level).orElse(null) instanceof EntityMaid maid)) {
            LOGGER.error("Could not load the maid seated on {} when the game stopped", dragon.getScoreboardName());
            return;
        }

        // Same place as when the seat drops her on logout
        maid.moveTo(dragon.getDismountLocationForPassenger(maid), dragon.getYRot(), 0);
        maid.setData(KnightlyMaid.FALL_PROTECTION, true);

        // Fails if an entity with her UUID is already loaded
        if (level.addFreshEntity(maid)) {
            LOGGER.info("Put down maid {} that was seated on {} when the game stopped", maid.getUUID(), dragon.getScoreboardName());
        } else {
            LOGGER.warn("Could not put down maid {} that was seated on {} when the game stopped", maid.getUUID(), dragon.getScoreboardName());
        }
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider) {
        unrestored.forEach((dragon, maid) -> tag.put(dragon.toString(), maid));

        for (ServerPlayer dragon : server.getPlayerList().getPlayers()) {
            CompoundTag maidTag = new CompoundTag();

            if (dragon.getFirstPassenger() instanceof DragonSeat seat && seat.getFirstPassenger() instanceof EntityMaid maid && maid.saveAsPassenger(maidTag)) {
                tag.put(dragon.getStringUUID(), maidTag);
            }
        }

        return tag;
    }

    /** The seated maids are only collected while saving, so the file is always rewritten: an outdated one would bring back maids already put down */
    @Override
    public boolean isDirty() {
        return true;
    }
}
