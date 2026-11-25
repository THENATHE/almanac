package com.thenathe.almanac.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The rain and thunder levels as the level holds them. On a client with Almanac {@link Level#getRainLevel} gives the
 * player's share of the rain where they stand ({@code ClientRain}); these are the world's own levels, as the server
 * sent them, and the thunder level isn't multiplied by the rain level as {@link Level#getThunderLevel} does.
 */
@Mixin(Level.class)
public interface LevelWeatherAccessor {
    @Accessor("rainLevel")
    float almanac$rainLevel();

    @Accessor("thunderLevel")
    float almanac$thunderLevel();
}
