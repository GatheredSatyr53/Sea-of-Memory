package com.seaofmemory.client.entity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class SnowPersonRenderState extends LivingEntityRenderState {
    public boolean aggressive;
    // Stable per entity, so each one twitches at its own moments.
    public int seed;
}
