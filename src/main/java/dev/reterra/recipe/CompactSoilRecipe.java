package dev.reterra.recipe;

import dev.reterra.Content;
import dev.reterra.machine.MachineLogic;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
//? if <1.21.2 {
import net.minecraft.recipe.SpecialRecipeSerializer;
//? } else {
/*import net.minecraft.recipe.SpecialCraftingRecipe.SpecialRecipeSerializer;
*///? }
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/** Four feedstock in a single slot, plus one compost. Existing spread recipes remain available. */
public final class CompactSoilRecipe extends SpecialCraftingRecipe {
  public static final RecipeSerializer<CompactSoilRecipe> SERIALIZER =
      new SpecialRecipeSerializer<>(CompactSoilRecipe::new);

  public CompactSoilRecipe(CraftingRecipeCategory category) {
    super(category);
  }

  public static void initialize() {
    Registry.register(Registries.RECIPE_SERIALIZER, Content.id("compact_soil"), SERIALIZER);
  }

  public static boolean pattern(List<ItemStack> stacks) {
    int soil = 0, compost = 0;
    for (ItemStack stack : stacks) {
      if (stack.isEmpty()) continue;
      if (MachineLogic.feedstock(stack)) soil++;
      else if (stack.isOf(Content.COMPOST)) compost++;
      else return false;
    }
    return soil == 1 && compost == 1;
  }

  @Override
  public boolean matches(CraftingRecipeInput input, World world) {
    return pattern(input.getStacks())
        && input.getStacks().stream().anyMatch(s -> MachineLogic.feedstock(s) && s.getCount() >= 4);
  }

  @Override
  public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
    return pattern(input.getStacks())
            && input.getStacks().stream()
                .anyMatch(s -> MachineLogic.feedstock(s) && s.getCount() >= 4)
        ? new ItemStack(Items.DIRT, 4)
        : ItemStack.EMPTY;
  }

  //? if <1.21.2 {
  @Override
  public boolean fits(int width, int height) {
    return width * height >= 2;
  }
  //? } else {
  /*@Override
  public net.minecraft.recipe.book.RecipeBookCategory getRecipeBookCategory() {
    return net.minecraft.recipe.book.RecipeBookCategories.CRAFTING_MISC;
  }
  *///? }

  @Override
  public RecipeSerializer<CompactSoilRecipe> getSerializer() {
    return SERIALIZER;
  }
}
