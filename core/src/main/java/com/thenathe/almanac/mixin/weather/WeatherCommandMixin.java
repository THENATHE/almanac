package com.thenathe.almanac.mixin.weather;

import com.thenathe.almanac.season.WeatherCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.WeatherCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With regional weather (from 0.4.0) {@code /weather} acts on the storms: around whoever runs it, or on the whole
 * world with {@code weatherCommandEverywhere} ({@link WeatherCommands}). Off, it is vanilla's.
 */
@Mixin(WeatherCommand.class)
abstract class WeatherCommandMixin {
    @Inject(method = "setClear", at = @At("HEAD"), cancellable = true)
    private static void almanac$clear(CommandSourceStack source, int duration, CallbackInfoReturnable<Integer> info) {
        if (WeatherCommands.regional(source)) {
            info.setReturnValue(WeatherCommands.vanillaCommand(source, WeatherCommands.Kind.CLEAR, duration));
        }
    }

    @Inject(method = "setRain", at = @At("HEAD"), cancellable = true)
    private static void almanac$rain(CommandSourceStack source, int duration, CallbackInfoReturnable<Integer> info) {
        if (WeatherCommands.regional(source)) {
            info.setReturnValue(WeatherCommands.vanillaCommand(source, WeatherCommands.Kind.RAIN, duration));
        }
    }

    @Inject(method = "setThunder", at = @At("HEAD"), cancellable = true)
    private static void almanac$thunder(CommandSourceStack source, int duration, CallbackInfoReturnable<Integer> info) {
        if (WeatherCommands.regional(source)) {
            info.setReturnValue(WeatherCommands.vanillaCommand(source, WeatherCommands.Kind.THUNDER, duration));
        }
    }
}
