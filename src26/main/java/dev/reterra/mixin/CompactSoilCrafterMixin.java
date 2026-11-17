package dev.reterra.mixin;

import dev.reterra.machine.MachineLogic;
import dev.reterra.recipe.CompactSoilRecipe;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrafterBlock.class)
public abstract class CompactSoilCrafterMixin {
  // Vanilla's recipe cache ignores stack counts; this recipe must check them on each pulse.
  @Inject(method = "getPotentialResults", at = @At("HEAD"), cancellable = true)
  private static void reterra$countedRecipe(
      ServerLevel world,
      CraftingInput input,
      CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> ci) {
    if (CompactSoilRecipe.pattern(input.items()))
      ci.setReturnValue(world.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, world));
  }

  @Redirect(
      method = "dispenseFrom",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/core/NonNullList;forEach(Ljava/util/function/Consumer;)V"))
  private void reterra$consume(
      NonNullList<ItemStack> stacks,
      Consumer<ItemStack> vanilla,
      BlockState state,
      ServerLevel world,
      BlockPos pos) {
    boolean compact =
        world
            .recipeAccess()
            .getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, stacks), world)
            .map(entry -> entry.value() instanceof CompactSoilRecipe)
            .orElse(false);
    for (ItemStack stack : stacks) {
      if (compact && MachineLogic.feedstock(stack)) stack.shrink(4);
      else vanilla.accept(stack);
    }
  }
}
