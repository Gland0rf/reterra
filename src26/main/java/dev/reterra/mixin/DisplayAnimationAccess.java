package dev.reterra.mixin;

import com.mojang.math.Transformation;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayAnimationAccess {
  @Invoker("setTransformation")
  void reterra$transform(Transformation transformation);

  @Invoker("setTransformationInterpolationDuration")
  void reterra$duration(int ticks);

  @Invoker("setTransformationInterpolationDelay")
  void reterra$start(int ticks);

  @Invoker("setPosRotInterpolationDuration")
  void reterra$movement(int ticks);

  @Invoker("setBrightnessOverride")
  void reterra$brightness(Brightness brightness);

  @Invoker("setWidth")
  void reterra$width(float width);

  @Invoker("setHeight")
  void reterra$height(float height);
}
