package dev.reterra.mixin;

import dev.reterra.world.WorldOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CropBlock.class)
public abstract class CropGrowthMixin {
  @Inject(method = "randomTick", at = @At("RETURN"))
  private void reterra$fertileGrowth(
      BlockState before, ServerLevel world, BlockPos pos, RandomSource random, CallbackInfo ci) {
    if (!world.dimension().equals(Level.OVERWORLD) || !WorldOptions.barren(world)) return;
    CropBlock crop = (CropBlock) (Object) this;
    BlockState after = world.getBlockState(pos), soil = world.getBlockState(pos.below());
    if (after.is(crop)
        && crop.getAge(after) > crop.getAge(before)
        && soil.getBlock() instanceof FarmlandBlock
        && soil.getValue(FarmlandBlock.MOISTURE) > 0)
      world.setBlock(pos, crop.getStateForAge(Math.min(crop.getMaxAge(), crop.getAge(after) + 3)), 2);
  }
}
