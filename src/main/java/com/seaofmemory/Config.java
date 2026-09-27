package com.seaofmemory;

import net.neoforged.neoforge.common.ModConfigSpec;

// Server config: stored per world and synced to clients.
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder().push("fog");

    public static final ModConfigSpec.IntValue FOG_UPDATE_INTERVAL = BUILDER
            .comment("How often, in ticks, fog density around players is recalculated")
            .defineInRange("updateInterval", 40, 1, 1200);

    public static final ModConfigSpec.IntValue FOG_SIMULATION_RADIUS = BUILDER
            .comment("Radius in chunks around each player where fog is simulated")
            .defineInRange("simulationRadius", 4, 1, 16);

    public static final ModConfigSpec.DoubleValue FOG_RISE_RATE = BUILDER
            .comment("Maximum density gained per update while fog gathers")
            .defineInRange("riseRate", 0.01, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue FOG_FALL_RATE = BUILDER
            .comment("Maximum density lost per update while fog clears; lower than riseRate so fog lingers")
            .defineInRange("fallRate", 0.005, 0.0, 1.0);

    static {
        BUILDER.pop().push("cold");
    }

    public static final ModConfigSpec.DoubleValue COLD_RISE_MULTIPLIER = BUILDER
            .comment("Multiplier for how fast cold builds up in the fog")
            .defineInRange("riseMultiplier", 1.0, 0.0, 10.0);

    public static final ModConfigSpec.BooleanValue COLD_FREEZE_DAMAGE = BUILDER
            .comment("Whether players at maximum cold take freezing damage")
            .define("freezeDamage", true);

    static final ModConfigSpec SPEC = BUILDER.pop().build();

    private Config() {
    }
}
