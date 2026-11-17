package dev.reterra.mixin;

import dev.reterra.world.WorldOptions;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CropBlock.class)
public abstract class CropGrowthMixin {
  @Inject(method = "randomTick", at = @At("RETURN"))
  private void reterra$fertileGrowth(
      BlockState before, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
    if (!world.getRegistryKey().equals(World.OVERWORLD) || !WorldOptions.barren(world)) return;
    CropBlock crop = (CropBlock) (Object) this;
    BlockState after = world.getBlockState(pos), soil = world.getBlockState(pos.down());
    if (after.isOf(crop)
        && crop.getAge(after) > crop.getAge(before)
        && soil.getBlock() instanceof FarmlandBlock
        && soil.get(FarmlandBlock.MOISTURE) > 0)
      world.setBlockState(pos, crop.withAge(Math.min(crop.getMaxAge(), crop.getAge(after) + 3)), 2);
  }
}
