package com.thenathe.almanac.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The thunder level on its own. {@link Level#getThunderLevel} gives it already multiplied by the rain level, but
 * vanilla's weather events send it plain, because the client multiplies it by its own rain level.
 */
@Mixin(Level.class)
public interface LevelWeatherAccessor {
    @Accessor("thunderLevel")
    float almanac$thunderLevel();
}
