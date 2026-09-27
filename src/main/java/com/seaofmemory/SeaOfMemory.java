package com.seaofmemory;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.seaofmemory.beacon.FogBeaconBlock;
import com.seaofmemory.cold.Cold;
import com.seaofmemory.entity.ModEntities;
import com.seaofmemory.fog.CognitiveFog;
import com.seaofmemory.sea.Absorption;
import com.seaofmemory.sea.MemoryDensityFunction;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(SeaOfMemory.MODID)
public class SeaOfMemory {
    public static final String MODID = "seaofmemory";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES = DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, MODID);

    public static final DeferredBlock<FogBeaconBlock> FOG_BEACON = BLOCKS.registerBlock("fog_beacon", FogBeaconBlock::new, p -> p
            .mapColor(MapColor.METAL)
            .strength(1.5f)
            .sound(SoundType.METAL)
            .noOcclusion()
            .lightLevel(state -> state.getValue(FogBeaconBlock.LIT) ? 15 : 0));
    public static final DeferredItem<BlockItem> FOG_BEACON_ITEM = ITEMS.registerSimpleBlockItem("fog_beacon", FOG_BEACON);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.seaofmemory"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> FOG_BEACON_ITEM.get().getDefaultInstance())
            // Every mod item goes here, in registration order.
            .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<MemoryDensityFunction>> MEMORY_DENSITY_FUNCTION =
            DENSITY_FUNCTION_TYPES.register("memory", () -> MemoryDensityFunction.MAP_CODEC);

    public SeaOfMemory(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        DENSITY_FUNCTION_TYPES.register(modEventBus);
        CognitiveFog.ATTACHMENT_TYPES.register(modEventBus);
        Cold.ATTACHMENT_TYPES.register(modEventBus);
        Absorption.ATTACHMENT_TYPES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }
}
