package dev.reterra.machine;

import dev.reterra.state.RestorationState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class MachineBlock extends Block {
  public static final net.minecraft.world.level.block.state.properties.BooleanProperty SUPPLIED =
      net.minecraft.world.level.block.state.properties.BooleanProperty.create("supplied");
  public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT =
      net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;

  public MachineBlock(Properties settings) {
    super(settings);
    registerDefaultState(defaultBlockState().setValue(SUPPLIED, false).setValue(LIT, false));
  }

  @Override
  protected void createBlockStateDefinition(
      net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(SUPPLIED, LIT);
  }

  public static void updateVisual(ServerLevel world, RestorationState.Machine machine) {
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
    if (old.getValue(SUPPLIED) != supplied || old.getValue(LIT) != active)
      world.setBlock(
          machine.pos, old.setValue(SUPPLIED, supplied).setValue(LIT, active), Block.UPDATE_CLIENTS);
  }
  @Override
  protected void affectNeighborsAfterRemoval(BlockState old, ServerLevel world, BlockPos pos, boolean moved) {
    removed(world, pos, old);
    super.affectNeighborsAfterRemoval(old, world, pos, moved);
  }

  private static void removed(ServerLevel server, BlockPos pos, BlockState old) {
    var state = RestorationState.get(server);
    var machine = state.machines.remove(pos.asLong());
    if (machine == null && old.is(dev.reterra.Content.CORE))
      machine = state.reclaimedRegions.get(pos.asLong());
    if (machine != null) Containers.dropContents(server, pos, machine);
    state.setDirty();
  }
}
