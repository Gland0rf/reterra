package dev.reterra.mixin;

import dev.reterra.state.RestorationState;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
  @Inject(
      method = "place(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z",
      at = @At("RETURN"))
  private void reterra$protectPlacedBlock(
      ItemPlacementContext context, BlockState placedState, CallbackInfoReturnable<Boolean> cir) {
    if (cir.getReturnValue() && context.getWorld() instanceof ServerWorld world) {
      RestorationState state = RestorationState.get(world);
      state.protectedBlocks.add(context.getBlockPos().asLong());
      state.markDirty();
      dev.reterra.machine.Stewardship.plant(world, context.getBlockPos());
    }
  }
}
