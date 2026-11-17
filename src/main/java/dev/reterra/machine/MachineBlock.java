package dev.reterra.machine;

import dev.reterra.state.RestorationState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class MachineBlock extends Block {
  public static final net.minecraft.state.property.BooleanProperty SUPPLIED =
      net.minecraft.state.property.BooleanProperty.of("supplied");
  public static final net.minecraft.state.property.BooleanProperty LIT =
      net.minecraft.state.property.Properties.LIT;

  public MachineBlock(Settings settings) {
    super(settings);
    setDefaultState(getDefaultState().with(SUPPLIED, false).with(LIT, false));
  }

  @Override
  protected void appendProperties(
      net.minecraft.state.StateManager.Builder<Block, BlockState> builder) {
    builder.add(SUPPLIED, LIT);
  }

  public static void updateVisual(ServerWorld world, RestorationState.Machine machine) {
    BlockState old = world.getBlockState(machine.pos);
    if (!(old.getBlock() instanceof MachineBlock)) return;
    boolean supplied =
        !machine.isEmpty()
            || machine.compost > 0
            || machine.fuel > 0
            || machine.water > 0
            || machine.culture > 0;
    boolean active =
        machine.kind.equals("heart_of_the_world")
            ? RestorationState.get(world).heartActivated
            : machine.reclaimed
                || (!machine.paused
                    && Multiblocks.issue(world, machine.pos, machine.kind) == 0
                    && (machine.kind.equals("restoration_core")
                        ? MachineLogic.status(world, machine) == MachineLogic.Status.READY
                        : machine.kind.equals("revival_chamber")
                        ? !machine.pendingSpecies.isEmpty()
                        : machine.fuel > 0
                            && MachineLogic.hasWaterSupply(world, machine)
                            && machine.compost > 0));
    if (old.get(SUPPLIED) != supplied || old.get(LIT) != active)
      world.setBlockState(
          machine.pos, old.with(SUPPLIED, supplied).with(LIT, active), Block.NOTIFY_LISTENERS);
  }

  //? if <1.21.5 {
  @Override
  public void onStateReplaced(BlockState old, World world, BlockPos pos, BlockState next, boolean moved) {
    if (!old.isOf(next.getBlock()) && world instanceof ServerWorld server) removed(server, pos, old);
    super.onStateReplaced(old, world, pos, next, moved);
  }
  //? } else {
  /*@Override
  protected void onStateReplaced(BlockState old, ServerWorld world, BlockPos pos, boolean moved) {
    removed(world, pos, old);
    super.onStateReplaced(old, world, pos, moved);
  }
  *///? }

  private static void removed(ServerWorld server, BlockPos pos, BlockState old) {
    var state = RestorationState.get(server);
    var machine = state.machines.remove(pos.asLong());
    if (machine == null && old.isOf(dev.reterra.Content.CORE))
      machine = state.reclaimedRegions.get(pos.asLong());
    if (machine != null) ItemScatterer.spawn(server, pos, machine);
    state.markDirty();
  }
}
