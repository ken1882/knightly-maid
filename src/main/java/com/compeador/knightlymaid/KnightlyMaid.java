package com.compeador.knightlymaid;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

import java.util.function.Supplier;

@Mod(KnightlyMaid.MODID)
public class KnightlyMaid {
    public static final String MODID = "knightly_maid";

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<DragonSeat>> DRAGON_SEAT = ENTITY_TYPES.register("dragon_seat",
            () -> EntityType.Builder.<DragonSeat>of(DragonSeat::new, MobCategory.MISC).sized(0, 0).noSummon().clientTrackingRange(10).build("dragon_seat"));

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);

    /** Set when a maid leaves a dragon seat in the air, so her next landing deals no fall damage */
    public static final Supplier<AttachmentType<Boolean>> FALL_PROTECTION = ATTACHMENT_TYPES.register("fall_protection",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).build());

    public KnightlyMaid(final IEventBus modBus, final ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, KnightlyMaidConfig.SPEC);
        ENTITY_TYPES.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
        modBus.addListener((RegisterDataMapTypesEvent event) -> event.register(MountSettings.DATA_MAP));
    }
}
