package com.seaofmemory;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

// Client-only entry point; not loaded on dedicated servers.
@Mod(value = SeaOfMemory.MODID, dist = Dist.CLIENT)
public class SeaOfMemoryClient {
    public SeaOfMemoryClient(ModContainer container) {
    }
}
