package dev.reterra.testmixin;

import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hydraulic fixtures use the normal Overworld waterline, including on flat test terrain. */
@Mixin(FlatChunkGenerator.class)
public abstract class FlatSeaLevelMixin {
  @Inject(method = "getSeaLevel", at = @At("HEAD"), cancellable = true)
  private void reterra$overworldWaterline(CallbackInfoReturnable<Integer> result) {
    result.setReturnValue(63);
  }
}
