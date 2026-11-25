package com.thenathe.almanac.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.synth.Noise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The temperature noise Almanac builds gives what vanilla's own gives, so seasonal snow lines sit where vanilla's do. */
class SeasonTemperatureTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theNoiseIsVanillas() throws ReflectiveOperationException {
        Field field = Biome.class.getDeclaredField("TEMPERATURE_NOISE");
        field.setAccessible(true);
        Noise vanilla = (Noise) field.get(null);
        for (int x = -40_000; x <= 40_000; x += 997) {
            for (int z = -40_000; z <= 40_000; z += 1_501) {
                assertEquals(vanilla.get(x / 8.0F, z / 8.0F), SeasonTemperature.NOISE.get(x / 8.0F, z / 8.0F), x + ", " + z);
            }
        }
    }
}
