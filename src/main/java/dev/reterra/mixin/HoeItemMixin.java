package dev.reterra.mixin;

import net.minecraft.block.Blocks;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HoeItem.class)
public abstract class HoeItemMixin {
  @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
  private void reterra$sterileSoil(
      ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
    var soil = context.getWorld().getBlockState(context.getBlockPos());
    if (soil.isOf(Blocks.COARSE_DIRT) || soil.isOf(Blocks.ROOTED_DIRT)) {
      if (!context.getWorld().isClient() && context.getPlayer() != null)
        context.getPlayer().sendMessage(Text.translatable("hint.reterra.sterile_soil"), true);
      cir.setReturnValue(ActionResult.FAIL);
    }
  }
}
