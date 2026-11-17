package dev.reterra.mixin;

import dev.reterra.machine.MachineLogic;
import dev.reterra.recipe.CompactSoilRecipe;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public abstract class CompactSoilCraftingMixin {
  @Shadow @Final private CraftingContainer craftSlots;
  @Unique private boolean reterra$compact;

  @Inject(method = "onTake", at = @At("HEAD"))
  private void reterra$select(Player player, ItemStack result, CallbackInfo ci) {
    reterra$compact = dev.reterra.compat.WorldAccess.entityWorld(player) instanceof net.minecraft.server.level.ServerLevel world
        && world.recipeAccess()
            .getRecipeFor(RecipeType.CRAFTING, craftSlots.asCraftInput(), world)
            .map(entry -> entry.value() instanceof CompactSoilRecipe).orElse(false);
  }

  @Redirect(
      method = "onTake",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/inventory/CraftingContainer;removeItem(II)Lnet/minecraft/world/item/ItemStack;"))
  private ItemStack reterra$consume(CraftingContainer inventory, int slot, int count) {
    return inventory.removeItem(
        slot, reterra$compact && MachineLogic.feedstock(inventory.getItem(slot)) ? 4 : count);
  }
}

