package dev.reterra.mixin;

import dev.reterra.world.BarrenWorld;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
  @Inject(method = "generateFeatures", at = @At("TAIL"))
  private void reterra$barren(
      StructureWorldAccess world, Chunk chunk, StructureAccessor accessor, CallbackInfo ci) {
    BarrenWorld.transform(world, chunk);
  }
}
