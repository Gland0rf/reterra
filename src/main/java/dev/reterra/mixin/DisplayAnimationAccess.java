package dev.reterra.mixin;

import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DisplayEntity.class)
public interface DisplayAnimationAccess {
  @Invoker("setTransformation")
  void reterra$transform(AffineTransformation transformation);

  @Invoker("setInterpolationDuration")
  void reterra$duration(int ticks);

  @Invoker("setStartInterpolation")
  void reterra$start(int ticks);

  @Invoker("setTeleportDuration")
  void reterra$movement(int ticks);

  @Invoker("setBrightness")
  void reterra$brightness(Brightness brightness);

  @Invoker("setDisplayWidth")
  void reterra$width(float width);

  @Invoker("setDisplayHeight")
  void reterra$height(float height);
}
