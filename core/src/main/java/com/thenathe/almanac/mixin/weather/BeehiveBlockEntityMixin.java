package com.thenathe.almanac.mixin.weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thenathe.almanac.climate.LocalWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A hive keeps its bees in during rain only where it rains on it (regional weather, from 0.4.0). */
@Mixin(BeehiveBlockEntity.class)
abstract class BeehiveBlockEntityMixin {
    @WrapOperation(method = "releaseOccupant", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/attribute/EnvironmentAttributeSystem;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/core/BlockPos;)Ljava/lang/Object;"))
    private static Object almanac$stormHere(EnvironmentAttributeSystem system, EnvironmentAttribute<?> attribute,
                                            BlockPos pos, Operation<Object> original,
                                            @Local(argsOnly = true) Level level) {
        Object value = original.call(system, attribute, pos);
        if (attribute == EnvironmentAttributes.BEES_STAY_IN_HIVE && !Boolean.TRUE.equals(value)
                && LocalWeather.active(level)) {
            return LocalWeather.rainingAt(level, pos.above());
        }
        return value;
    }
}
