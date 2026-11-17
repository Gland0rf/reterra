package dev.reterra.mixin;

import dev.reterra.machine.MachineLogic;
import dev.reterra.recipe.CompactSoilRecipe;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.slot.CraftingResultSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingResultSlot.class)
public abstract class CompactSoilCraftingMixin {
  @Shadow @Final private RecipeInputInventory input;
  @Unique private boolean reterra$compact;

  @Inject(method = "onTakeItem", at = @At("HEAD"))
  private void reterra$select(PlayerEntity player, ItemStack result, CallbackInfo ci) {
    //? if <1.21.2 {
    reterra$compact =
        player
            .getWorld()
            .getRecipeManager()
            .getFirstMatch(RecipeType.CRAFTING, input.createRecipeInput(), dev.reterra.compat.WorldAccess.entityWorld(player))
            .map(entry -> entry.value() instanceof CompactSoilRecipe)
            .orElse(false);
    //? } else {
    /*reterra$compact = dev.reterra.compat.WorldAccess.entityWorld(player) instanceof net.minecraft.server.world.ServerWorld world
        && world.getRecipeManager()
            .getFirstMatch(RecipeType.CRAFTING, input.createRecipeInput(), world)
            .map(entry -> entry.value() instanceof CompactSoilRecipe).orElse(false);
    *///? }
  }

  @Redirect(
      method = "onTakeItem",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/inventory/RecipeInputInventory;removeStack(II)Lnet/minecraft/item/ItemStack;"))
  private ItemStack reterra$consume(RecipeInputInventory inventory, int slot, int count) {
    return inventory.removeStack(
        slot, reterra$compact && MachineLogic.feedstock(inventory.getStack(slot)) ? 4 : count);
  }
}
