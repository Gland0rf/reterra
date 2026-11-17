package dev.reterra.mixin;

import dev.reterra.state.RestorationState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
  @Inject(
      method = "placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z",
      at = @At("RETURN"))
  private void reterra$protectPlacedBlock(
      BlockPlaceContext context, BlockState placedState, CallbackInfoReturnable<Boolean> cir) {
    if (cir.getReturnValue() && context.getLevel() instanceof ServerLevel world) {
      RestorationState state = RestorationState.get(world);
      state.protectedBlocks.add(context.getClickedPos().asLong());
      state.setDirty();
      dev.reterra.machine.Stewardship.plant(world, context.getClickedPos());
    }
  }
}
