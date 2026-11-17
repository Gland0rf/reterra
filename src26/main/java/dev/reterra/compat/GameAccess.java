package dev.reterra.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

/** Registry-backed constants shared by the 26.x ports. */
public final class GameAccess {
  private GameAccess() {}
  @SuppressWarnings("unchecked")
  private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entity(String id) {
    return (EntityType<T>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(id));
  }
  public static final EntityType<net.minecraft.world.entity.animal.bee.Bee> BEE = entity("bee");
  public static final EntityType<net.minecraft.world.entity.animal.cow.Cow> COW = entity("cow");
  public static final EntityType<net.minecraft.world.entity.animal.chicken.Chicken> CHICKEN = entity("chicken");
  public static final EntityType<net.minecraft.world.entity.animal.sheep.Sheep> SHEEP = entity("sheep");
  public static final EntityType<net.minecraft.world.entity.animal.fish.Cod> COD = entity("cod");
  public static final EntityType<net.minecraft.world.entity.Display.BlockDisplay> BLOCK_DISPLAY = entity("block_display");
  public static final Block COPPER_BLOCK = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_block"));
  public static final Block CUT_COPPER = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("cut_copper"));
  public static final Block CUT_COPPER_SLAB = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("cut_copper_slab"));
  public static final Block LIGHTNING_ROD = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("lightning_rod"));
  public static final net.minecraft.world.level.material.PushReaction IMMOVABLE =
      //? if >=26.3 {
      /*net.minecraft.world.level.material.PushReaction.IMMOVEABLE;*/
      //? } else {
      net.minecraft.world.level.material.PushReaction.BLOCK;
      //? }
  public static boolean sapling(net.minecraft.world.level.block.state.BlockState state) {
    return state.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock;
  }
  public static void giveOrDrop(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack stack) {
    if (player.getInventory().add(stack)) return;
    //? if >=26.3 {
    /*player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);*/
    //? } else {
    player.drop(stack, false);
    //? }
  }
}
