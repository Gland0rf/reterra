package dev.reterra.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/** Small API differences shared by restoration and water simulation. */
public final class WorldAccess {
  private WorldAccess() {}

  public static net.minecraft.world.level.Level entityWorld(net.minecraft.world.entity.Entity entity) {
    return entity.level();
  }

  public static net.minecraft.world.phys.Vec3 position(net.minecraft.world.entity.Entity entity) {
    return entity.position();
  }

  public static net.minecraft.server.level.ServerLevel world(
      net.minecraft.server.level.ServerPlayer player) {
    return (net.minecraft.server.level.ServerLevel) entityWorld(player);
  }

  public static boolean gamemaster(net.minecraft.commands.CommandSourceStack source) {
    return net.minecraft.commands.Commands.hasPermission(
        net.minecraft.commands.Commands.LEVEL_GAMEMASTERS).test(source);
  }

  public static BlockPos spawn(net.minecraft.server.level.ServerLevel world) {
    return world.getRespawnData().pos();
  }

  public static boolean canModify(net.minecraft.world.level.Level world,
      net.minecraft.world.entity.player.Player player, BlockPos pos) {
    return world.mayInteract(player, pos);
  }

  public static boolean flower(net.minecraft.world.item.ItemStack stack) {
    return stack.getItem() instanceof net.minecraft.world.item.BlockItem item
        && item.getBlock().defaultBlockState().is(net.minecraft.tags.BlockTags.FLOWERS);
  }

  public static boolean opaque(BlockGetter world, BlockPos pos) {
    return world.getBlockState(pos).isSolidRender();
  }
}
