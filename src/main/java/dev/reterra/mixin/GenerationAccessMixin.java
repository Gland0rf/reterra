package dev.reterra.mixin;

import dev.reterra.world.BarrenWorld;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops a later neighbor's tree or plant feature spilling life into an already finished chunk. */
@Mixin(ChunkRegion.class)
public abstract class GenerationAccessMixin {
  @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
  private void reterra$preventLivingFeatures(
      BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> cir) {
    ChunkRegion world = (ChunkRegion) (Object) this;
    if (!dev.reterra.world.WorldOptions.barren(world.toServerWorld())) return;
    if (world.toServerWorld().getRegistryKey().equals(World.OVERWORLD)
        && !state.isOf(net.minecraft.block.Blocks.COARSE_DIRT)
        && (state.isIn(net.minecraft.registry.tag.BlockTags.DIRT)
            || state.isOf(net.minecraft.block.Blocks.FARMLAND)
            || state.isOf(net.minecraft.block.Blocks.DIRT_PATH)
            || state.isOf(net.minecraft.block.Blocks.MOSS_BLOCK))) {
      cir.setReturnValue(
          world.setBlockState(
              pos, net.minecraft.block.Blocks.COARSE_DIRT.getDefaultState(), flags, depth));
      return;
    }
    if (world.toServerWorld().getRegistryKey().equals(World.OVERWORLD)
        && BarrenWorld.lostToCatastrophe(pos, state)) cir.setReturnValue(true);
  }
}
