package com.compeador.knightlymaid;

import by.dragonsurvivalteam.dragonsurvival.DragonSurvival;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider;
import by.dragonsurvivalteam.dragonsurvival.registry.attachments.MovementData;
import by.dragonsurvivalteam.dragonsurvival.server.handlers.DragonRidingHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The only class that uses Dragon Survival (an optional dependency). <br>
 * Only call it after checking {@link DragonSeat#DRAGON_SURVIVAL}, so its classes are never loaded without the mod.
 */
final class DragonCompat {
    private DragonCompat() { /* Nothing to do */ }

    static boolean isRideableDragon(final Player player) {
        return DragonStateProvider.isDragon(player) && DragonRidingHandler.dragonIsRideable(player);
    }

    static float getBodyYaw(final Player dragon) {
        return (float) MovementData.getData(dragon).bodyYaw;
    }

    /** Where DS seats player riders. Only known on the client: DS syncs it to the server for player riders only. */
    static @Nullable Vec3 getMountingBoneOffset(final Player dragon) {
        return DragonSurvival.PROXY.getDragonBoneOffset(dragon, DragonRidingHandler.MOUNTING_BONE);
    }
}
