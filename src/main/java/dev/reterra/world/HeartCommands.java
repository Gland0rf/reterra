package dev.reterra.world;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.CommandDispatcher;
import dev.reterra.Content;
import dev.reterra.machine.Multiblocks;
import dev.reterra.machine.RestorationEngine;
import dev.reterra.state.RestorationState;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Operator shortcut for replaying the real awakening without completing ecosystems. */
public final class HeartCommands {
  private HeartCommands() {}

  public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
    dispatcher.register(literal("reterra").then(literal("heart")
        .requires(dev.reterra.compat.WorldAccess::gamemaster)
        .then(literal("activate")
            .executes(context -> activate(context.getSource(), null))
            .then(argument("pos", BlockPosArgumentType.blockPos())
                .executes(context -> activate(context.getSource(),
                    BlockPosArgumentType.getBlockPos(context, "pos")))))));
  }

  private static int activate(ServerCommandSource source, BlockPos target) {
    var world = source.getWorld();
    if (!world.getRegistryKey().equals(World.OVERWORLD)) {
      source.sendError(Text.literal("Use this command in the Overworld."));
      return 0;
    }
    var state = RestorationState.get(world);
    if (state.heartActivated || state.heartAwakening > 0) {
      source.sendError(Text.literal("The Heart is already active or awakening. Use /reterra heart disable first."));
      return 0;
    }
    if (target == null && source.getPlayer() != null) {
      var hit = source.getPlayer().raycast(16, 1, false);
      if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block
          && RestorationEngine.loaded(world, block.getBlockPos())
          && world.getBlockState(block.getBlockPos()).isOf(Content.HEART)) target = block.getBlockPos();
    }
    if (target == null) {
      var origin = source.getPosition();
      double nearest = 64 * 64;
      for (var machine : state.machines.values()) {
        if (!machine.kind.equals("heart_of_the_world") || !RestorationEngine.loaded(world, machine.pos)) continue;
        double dx = machine.pos.getX() + .5 - origin.x;
        double dy = machine.pos.getY() + .5 - origin.y;
        double dz = machine.pos.getZ() + .5 - origin.z;
        double distance = dx * dx + dy * dy + dz * dz;
        if (distance < nearest && world.getBlockState(machine.pos).isOf(Content.HEART)) {
          nearest = distance;
          target = machine.pos;
        }
      }
    }
    if (target == null || !RestorationEngine.loaded(world, target)
        || !world.getBlockState(target).isOf(Content.HEART)) {
      source.sendError(Text.literal("Look at a Heart, or use /reterra heart activate <x> <y> <z>."));
      return 0;
    }
    if (Multiblocks.issue(world, target, "heart_of_the_world") != 0) {
      source.sendError(Text.literal("Complete the Heart's supports first."));
      return 0;
    }
    var machine = state.machines.computeIfAbsent(target.asLong(),
        key -> new RestorationState.Machine(BlockPos.fromLong(key), "heart_of_the_world"));
    machine.dirty = state::markDirty;
    if (!HeartAwakening.startForTesting(world, machine)) return 0;
    source.sendFeedback(() -> Text.literal("Heart awakening started. Ecosystem requirements bypassed for testing."), true);
    return 1;
  }
}
