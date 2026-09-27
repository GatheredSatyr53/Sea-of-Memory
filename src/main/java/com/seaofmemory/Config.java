package com.seaofmemory;

import java.util.Locale;

import com.seaofmemory.entity.FogSpawner;

import net.neoforged.neoforge.common.ModConfigSpec;

// Server config: config/seaofmemory-server.toml, overridable per world in <world>/serverconfig/; synced to clients.
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

    public static final ModConfigSpec.IntValue ABSORPTION_SECONDS = BUILDER
            .comment("Seconds a player has to stay in critical fog before it pulls them into the fog world")
            .defineInRange("absorptionSeconds", 15, 1, 600);

    public static final ModConfigSpec.IntValue IMPRINT_AFTER_DAYS = BUILDER
            .comment("In-game days players must have spent around a real world chunk before what they built there carries over into the fog world")
            .defineInRange("imprintAfterDays", 3, 0, 1000);

    static {
        BUILDER.pop().push("cold");
    }

    public static final ModConfigSpec.DoubleValue COLD_RISE_MULTIPLIER = BUILDER
            .comment("Multiplier for how fast cold builds up in the fog")
            .defineInRange("riseMultiplier", 1.0, 0.0, 10.0);

    public static final ModConfigSpec.BooleanValue COLD_FREEZE_DAMAGE = BUILDER
            .comment("Whether players at maximum cold take freezing damage")
            .define("freezeDamage", true);

    static {
        BUILDER.pop().push("snowPeople");
    }

    public static final ModConfigSpec.IntValue SNOW_PEOPLE_CAP = BUILDER
            .comment(String.format(Locale.ROOT, "Most snow people that rise around one player (within %d blocks)", (int) FogSpawner.CAP_RADIUS))
            .defineInRange("cap", 3, 0, 64);

    public static final ModConfigSpec.DoubleValue SNOW_PEOPLE_SPAWN_CHANCE = BUILDER
            .comment(String.format(Locale.ROOT, "Chance, checked every %d seconds per player in critical fog or the fog world, that one more tries to rise",
                    FogSpawner.SNOW_PEOPLE_INTERVAL / 20))
            .defineInRange("spawnChance", 0.2, 0.0, 1.0);

    static final ModConfigSpec SPEC = BUILDER.pop().build();

    private Config() {
    }
}
