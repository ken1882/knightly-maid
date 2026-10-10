package com.compeador.knightlymaid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Per entity type settings, from the data map {@code knightly_maid:mount_settings}
 * ({@code data/<namespace>/data_maps/entity_type/mount_settings.json}). Synced, since the client places the seat too.
 *
 * @param seatOffset    where a carrier ({@link DragonSeat#canCarry}) seats the maid, relative to its feet and turned with its body:
 *                      x left, y up, z forward. Without it the carrier's own passenger placement is used.
 * @param speedModifier walk target speed modifier while the maid drives this mount ({@link MaidMounts#gallopSpeed})
 */
public record MountSettings(Optional<Vec3> seatOffset, Optional<Float> speedModifier) {
    public static final Codec<MountSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Vec3.CODEC.optionalFieldOf("seat_offset").forGetter(MountSettings::seatOffset),
            ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("speed_modifier").forGetter(MountSettings::speedModifier)
    ).apply(instance, MountSettings::new));

    public static final DataMapType<EntityType<?>, MountSettings> DATA_MAP = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(KnightlyMaid.MODID, "mount_settings"), Registries.ENTITY_TYPE, CODEC).synced(CODEC, false).build();

    public static @Nullable MountSettings get(final Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).getData(DATA_MAP);
    }
}
