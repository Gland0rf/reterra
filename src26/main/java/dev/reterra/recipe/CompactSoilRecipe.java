package dev.reterra.recipe;

import dev.reterra.Content;
import dev.reterra.machine.MachineLogic;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Four feedstock in a single slot, plus one compost. Existing spread recipes remain available. */
public final class CompactSoilRecipe extends CustomRecipe {
  public static final RecipeSerializer<CompactSoilRecipe> SERIALIZER =
      new RecipeSerializer<>(com.mojang.serialization.MapCodec.unit(CompactSoilRecipe::new),
          net.minecraft.network.codec.StreamCodec.unit(new CompactSoilRecipe()));

  public CompactSoilRecipe() {
    super();
  }

  public static void initialize() {
    Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Content.id("compact_soil"), SERIALIZER);
  }

  public static boolean pattern(List<ItemStack> stacks) {
    int soil = 0, compost = 0;
    for (ItemStack stack : stacks) {
      if (stack.isEmpty()) continue;
      if (MachineLogic.feedstock(stack)) soil++;
      else if (stack.is(Content.COMPOST)) compost++;
      else return false;
    }
    return soil == 1 && compost == 1;
  }

  @Override
  public boolean matches(CraftingInput input, Level world) {
    return pattern(input.items())
        && input.items().stream().anyMatch(s -> MachineLogic.feedstock(s) && s.getCount() >= 4);
  }

  @Override
  public ItemStack assemble(CraftingInput input) {
    return pattern(input.items())
            && input.items().stream()
                .anyMatch(s -> MachineLogic.feedstock(s) && s.getCount() >= 4)
        ? new ItemStack(Items.DIRT, 4)
        : ItemStack.EMPTY;
  }
  @Override
  public net.minecraft.world.item.crafting.RecipeBookCategory recipeBookCategory() {
    return net.minecraft.world.item.crafting.RecipeBookCategories.CRAFTING_MISC;
  }

  @Override
  public RecipeSerializer<CompactSoilRecipe> getSerializer() {
    return SERIALIZER;
  }
}
