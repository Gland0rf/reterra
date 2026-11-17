package dev.reterra.mixin;

import dev.reterra.world.BarrenWorld;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
  @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
  private void reterra$barren(
      WorldGenLevel world, ChunkAccess chunk, StructureManager accessor, CallbackInfo ci) {
    BarrenWorld.transform(world, chunk);
  }
}
