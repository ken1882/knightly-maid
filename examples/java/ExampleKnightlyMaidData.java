package com.example.mymod.datagen;

import com.compeador.knightlymaid.MountSettings;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Example datagen for a mod that adds its own entities to Knightly Maid (NeoForge 1.21.1).
 * Register both providers in your GatherDataEvent handler (server data).
 */
public final class ExampleKnightlyMaidData {
    // Tags only need the id: no dependency on Knightly Maid
    static final TagKey<EntityType<?>> MAID_MOUNTS = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse("knightly_maid:maid_mounts"));
    static final TagKey<EntityType<?>> MAID_CARRIERS = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse("knightly_maid:maid_carriers"));

    public static class Tags extends EntityTypeTagsProvider {
        public Tags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
            super(output, lookup, "mymod", files);
        }

        @Override
        protected void addTags(HolderLookup.Provider lookup) {
            tag(MAID_MOUNTS).add(MyEntities.RAPTOR.get());      // she drives it (it must accept a mob as controlling passenger)
            tag(MAID_CARRIERS).add(MyEntities.WYVERN.get());    // she sits on it, someone else steers
        }
    }

    // The data map needs Knightly Maid at compile time (compileOnly), and the condition keeps the file harmless without it
    public static class DataMaps extends DataMapProvider {
        public DataMaps(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup);
        }

        @Override
        protected void gather() {
            builder(MountSettings.DATA_MAP)
                    .conditions(new ModLoadedCondition("knightly_maid"))
                    // x left, y up, z forward, from the carrier's feet, turned with its body
                    .add(MyEntities.WYVERN, new MountSettings(Optional.of(new Vec3(0, 1.8, -0.9)), Optional.empty()), false)
                    .add(MyEntities.RAPTOR, new MountSettings(Optional.empty(), Optional.of(1.3F)), false);
        }
    }
}
