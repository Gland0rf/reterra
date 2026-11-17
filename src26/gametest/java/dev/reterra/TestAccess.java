package dev.reterra;

import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Version adapters for test fixtures; all gameplay assertions remain shared. */
final class TestAccess {
  static CommandSourceStack gamemasterSource(ServerPlayer player) {
    return player.createCommandSourceStack().withPermission(
        net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER);
  }

  static void execute(CommandSourceStack source, String command) {
    source.getServer().getCommands().performPrefixedCommand(source, command);
  }

  static <T extends Entity> T create(EntityType<T> type, ServerLevel world) {
    return type.create(world, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
  }

  static Optional<RecipeHolder<?>> recipe(ServerLevel world, String name) {
    return world.recipeAccess().byKey(net.minecraft.resources.ResourceKey.create(
        net.minecraft.core.registries.Registries.RECIPE, Content.id(name)));
  }

  static boolean unlocked(ServerPlayer player, String name) {
    return player.getRecipeBook().contains(net.minecraft.resources.ResourceKey.create(
        net.minecraft.core.registries.Registries.RECIPE, Content.id(name)));
  }

  static void time(net.minecraft.server.level.ServerLevel world, long ticks) {
    var clock = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_CLOCK)
        .getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD);
    world.clockManager().setTotalTicks(clock, ticks);
  }
  static void inventoryChanged(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
    //? if >=26.2 {
    /*net.minecraft.advancements.triggers.CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), stack);*/
    //? } else {
    net.minecraft.advancements.CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), stack);
    //? }
  }
}
