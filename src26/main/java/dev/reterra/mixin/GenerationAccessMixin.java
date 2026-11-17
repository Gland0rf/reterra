package dev.reterra.mixin;

import dev.reterra.world.BarrenWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops a later neighbor's tree or plant feature spilling life into an already finished chunk. */
@Mixin(WorldGenRegion.class)
public abstract class GenerationAccessMixin {
  @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
  private void reterra$preventLivingFeatures(
      BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> cir) {
    WorldGenRegion world = (WorldGenRegion) (Object) this;
    if (!dev.reterra.world.WorldOptions.barren(world.getLevel())) return;
    if (world.getLevel().dimension().equals(Level.OVERWORLD)
        && !state.is(net.minecraft.world.level.block.Blocks.COARSE_DIRT)
        && (state.is(net.minecraft.tags.BlockTags.DIRT)
            || state.is(net.minecraft.world.level.block.Blocks.FARMLAND)
            || state.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)
            || state.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK))) {
      cir.setReturnValue(
          world.setBlock(
              pos, net.minecraft.world.level.block.Blocks.COARSE_DIRT.defaultBlockState(), flags, depth));
      return;
    }
    if (world.getLevel().dimension().equals(Level.OVERWORLD)
        && BarrenWorld.lostToCatastrophe(pos, state)) cir.setReturnValue(true);
  }
}
