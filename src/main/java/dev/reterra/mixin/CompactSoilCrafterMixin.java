package dev.reterra.mixin;

import dev.reterra.machine.MachineLogic;
import dev.reterra.recipe.CompactSoilRecipe;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.block.BlockState;
import net.minecraft.block.CrafterBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrafterBlock.class)
public abstract class CompactSoilCrafterMixin {
  // Vanilla's recipe cache ignores stack counts; this recipe must check them on each pulse.
  @Inject(method = "getCraftingRecipe", at = @At("HEAD"), cancellable = true)
  private static void reterra$countedRecipe(
      //? if <1.21.2 {
      World world,
      //? } else {
      /*ServerWorld world,
      *///? }
      CraftingRecipeInput input,
      CallbackInfoReturnable<Optional<RecipeEntry<CraftingRecipe>>> ci) {
    if (CompactSoilRecipe.pattern(input.getStacks()))
      ci.setReturnValue(world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world));
  }

  @Redirect(
      method = "craft",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/util/collection/DefaultedList;forEach(Ljava/util/function/Consumer;)V"))
  private void reterra$consume(
      DefaultedList<ItemStack> stacks,
      Consumer<ItemStack> vanilla,
      BlockState state,
      ServerWorld world,
      BlockPos pos) {
    boolean compact =
        world
            .getRecipeManager()
            .getFirstMatch(RecipeType.CRAFTING, CraftingRecipeInput.create(3, 3, stacks), world)
            .map(entry -> entry.value() instanceof CompactSoilRecipe)
            .orElse(false);
    for (ItemStack stack : stacks) {
      if (compact && MachineLogic.feedstock(stack)) stack.decrement(4);
      else vanilla.accept(stack);
    }
  }
}
