package com.thenathe.almanac.mixin.weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thenathe.almanac.climate.LocalWeather;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bees head for their hive in rain only where it rains on them (regional weather, from 0.4.0). Vanilla's
 * "bees stay in the hive" comes from the weather's environment layer, which reads the world's one weather; with
 * regional weather the storm over the bee counts as well.
 */
@Mixin(Bee.class)
abstract class BeeMixin {
    @WrapOperation(method = "wantsToEnterHive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/attribute/EnvironmentAttributeSystem;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/world/phys/Vec3;)Ljava/lang/Object;"))
    private Object almanac$stormHere(EnvironmentAttributeSystem system, EnvironmentAttribute<?> attribute, Vec3 pos,
                                     Operation<Object> original) {
        Object value = original.call(system, attribute, pos);
        Bee bee = (Bee) (Object) this;
        if (attribute == EnvironmentAttributes.BEES_STAY_IN_HIVE && !Boolean.TRUE.equals(value)
                && LocalWeather.active(bee.level())) {
            return LocalWeather.rainingAt(bee.level(), bee.blockPosition());
        }
        return value;
    }
}
