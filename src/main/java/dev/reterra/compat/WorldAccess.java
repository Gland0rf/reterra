package dev.reterra.compat;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/** Small API differences shared by restoration and water simulation. */
public final class WorldAccess {
  private WorldAccess() {}

  public static net.minecraft.world.World entityWorld(net.minecraft.entity.Entity entity) {
    //? if <1.21.9 {
    return entity.getWorld();
    //? } else {
    /*return entity.getEntityWorld();
    *///? }
  }

  public static net.minecraft.util.math.Vec3d position(net.minecraft.entity.Entity entity) {
    //? if <1.21.9 {
    return entity.getPos();
    //? } else {
    /*return entity.getEntityPos();
    *///? }
  }

  public static net.minecraft.server.world.ServerWorld world(
      net.minecraft.server.network.ServerPlayerEntity player) {
    return (net.minecraft.server.world.ServerWorld) entityWorld(player);
  }

  public static boolean gamemaster(net.minecraft.server.command.ServerCommandSource source) {
    //? if <1.21.11 {
    return source.hasPermissionLevel(2);
    //? } else {
    /*return net.minecraft.server.command.CommandManager.requirePermissionLevel(
        net.minecraft.server.command.CommandManager.GAMEMASTERS_CHECK).test(source);
    *///? }
  }

  public static BlockPos spawn(net.minecraft.server.world.ServerWorld world) {
    //? if <1.21.9 {
    return world.getSpawnPos();
    //? } else {
    /*return world.getSpawnPoint().getPos();
    *///? }
  }

  public static boolean canModify(net.minecraft.world.World world,
      net.minecraft.entity.player.PlayerEntity player, BlockPos pos) {
    //? if <1.21.5 {
    return world.canPlayerModifyAt(player, pos);
    //? } else {
    /*return world.canEntityModifyAt(player, pos);
    *///? }
  }

  public static boolean flower(net.minecraft.item.ItemStack stack) {
    return stack.getItem() instanceof net.minecraft.item.BlockItem item
        && item.getBlock().getDefaultState().isIn(net.minecraft.registry.tag.BlockTags.FLOWERS);
  }

  public static boolean opaque(BlockView world, BlockPos pos) {
    //? if <1.21.2 {
    return world.getBlockState(pos).isOpaqueFullCube(world, pos);
    //? } else {
    /*return world.getBlockState(pos).isOpaqueFullCube();
    *///? }
  }
}
