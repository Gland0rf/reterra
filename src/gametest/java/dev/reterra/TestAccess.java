package dev.reterra;

import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Version adapters for test fixtures; all gameplay assertions remain shared. */
final class TestAccess {
  static ServerCommandSource gamemasterSource(ServerPlayerEntity player) {
    //? if <1.21.11 {
    return player.getCommandSource().withLevel(2);
    //? } else {
    /*return player.getCommandSource().withPermissions(
        net.minecraft.command.permission.LeveledPermissionPredicate.GAMEMASTERS);
    *///? }
  }

  static void execute(ServerCommandSource source, String command) {
    //? if <1.21.10 {
    source.getServer().getCommandManager().executeWithPrefix(source, command);
    //? } else {
    /*source.getServer().getCommandManager().parseAndExecute(source, command);
    *///? }
  }

  static <T extends Entity> T create(EntityType<T> type, ServerWorld world) {
    //? if <1.21.2 {
    return type.create(world);
    //? } else {
    /*return type.create(world, net.minecraft.entity.SpawnReason.TRIGGERED);
    *///? }
  }

  static Optional<RecipeEntry<?>> recipe(ServerWorld world, String name) {
    //? if <1.21.2 {
    return world.getRecipeManager().get(Content.id(name));
    //? } else {
    /*return world.getRecipeManager().get(net.minecraft.registry.RegistryKey.of(
        net.minecraft.registry.RegistryKeys.RECIPE, Content.id(name)));
    *///? }
  }

  static boolean unlocked(ServerPlayerEntity player, String name) {
    //? if <1.21.2 {
    return player.getRecipeBook().contains(Content.id(name));
    //? } else {
    /*return player.getRecipeBook().isUnlocked(net.minecraft.registry.RegistryKey.of(
        net.minecraft.registry.RegistryKeys.RECIPE, Content.id(name)));
    *///? }
  }
}
