package dev.reterra;

import dev.reterra.effect.AnimationPayload.Kind;
import dev.reterra.effect.AnimationSignals;
import dev.reterra.machine.MachineLogic;
import dev.reterra.machine.RestorationEngine;
import dev.reterra.state.RestorationState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
public class AnimationGameTests {
  private static final String EMPTY_STRUCTURE = "fabric-gametest-api-v1:empty";
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void pumpStatusRecognizesPondAndCauldronIntakes(GameTestHelper test) {
    var world = test.getLevel();
    var pos = test.absolutePos(new BlockPos(3, 12, 3));
    var pump = new RestorationState.Machine(pos, "water_restorer");
    world.setBlockAndUpdate(pos, Content.WATERWORKS.defaultBlockState());
    world.setBlockAndUpdate(pos.below(), dev.reterra.compat.GameAccess.COPPER_BLOCK.defaultBlockState());
    world.setBlockAndUpdate(pos.north(), Blocks.WATER_CAULDRON.defaultBlockState());
    pump.compost = 8;
    pump.fuel = 128;
    world.setBlockAndUpdate(pos.east(2), Blocks.WATER.defaultBlockState());
    TestChecks.isTrue(test, 
        MachineLogic.status(world, pump) == MachineLogic.Status.READY,
        "A pond-fed pump must not ask for water just because its internal tank is empty.");
    dev.reterra.machine.MachineBlock.updateVisual(world, pump);
    TestChecks.isTrue(test, 
        world.getBlockState(pos).getValue(dev.reterra.machine.MachineBlock.LIT),
        "A fueled and filtered pond-fed pump must show its working indicator.");
    world.setBlockAndUpdate(pos.east(2), Blocks.AIR.defaultBlockState());
    world.setBlockAndUpdate(
        pos.north(),
        Blocks.WATER_CAULDRON
            .defaultBlockState()
            .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
    TestChecks.isTrue(test, 
        MachineLogic.status(world, pump) == MachineLogic.Status.READY,
        "A full intake cauldron is also a usable water supply.");
    world.setBlockAndUpdate(pos.north(), Blocks.WATER_CAULDRON.defaultBlockState());
    dev.reterra.machine.MachineBlock.updateVisual(world, pump);
    TestChecks.isTrue(test, 
        MachineLogic.status(world, pump) == MachineLogic.Status.WATER
            && !world.getBlockState(pos).getValue(dev.reterra.machine.MachineBlock.LIT),
        "Without stored water, a pond or a full cauldron, the pump must request water.");
    world.setBlockAndUpdate(pos.east(2), Blocks.WATER.defaultBlockState());
    pump.paused = true;
    dev.reterra.machine.MachineBlock.updateVisual(world, pump);
    TestChecks.isTrue(test, 
        MachineLogic.status(world, pump) == MachineLogic.Status.PAUSED
            && !world.getBlockState(pos).getValue(dev.reterra.machine.MachineBlock.LIT),
        "An available intake must not override the pause control.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void animationCuesAreBoundedTransientAndCanceledWithTheHeart(GameTestHelper test) {
    var world = test.getLevel();
    var state = RestorationState.get(world);
    var previous = new java.util.LinkedHashMap<>(state.visualSignals);
    try {
      state.visualSignals.clear();
      var origin = test.absolutePos(BlockPos.ZERO);
      for (int i = 0; i < 500; i++)
        AnimationSignals.emit(world, origin, origin.east(i), Kind.CORE, 0);
      TestChecks.isTrue(test, 
          state.visualSignals.size() == 1,
          "A batch of terrain writes must produce one trail cue, not a packet for every block.");
      for (int i = 0; i < 500; i++)
        AnimationSignals.emit(world, origin.east(i), origin, Kind.CORE, 0);
      TestChecks.isTrue(test, 
          state.visualSignals.size() == AnimationSignals.MAX_QUEUED,
          "Visual traffic must have a hard server-side bound.");
      var copy =
          RestorationState.fromNbt(
              state.writeNbt(new CompoundTag(), world.registryAccess()),
              world.registryAccess());
      TestChecks.isTrue(test, 
          copy.visualSignals.isEmpty(), "Old trails must never replay after loading a save.");
      state.visualSignals.clear();
      AnimationSignals.emit(world, origin, origin, Kind.HEART, 170);
      AnimationSignals.emit(world, origin, origin, Kind.HEART_FINISH, 0);
      AnimationSignals.stopHeart(world, origin);
      TestChecks.isTrue(test, 
          state.visualSignals.size() == 1
              && state.visualSignals.values().iterator().next().kind() == Kind.HEART_STOP,
          "Disabling the Heart must replace pending ornaments and waves with a stop cue.");
      AnimationSignals.flush(world);
      TestChecks.isTrue(test, 
          state.visualSignals.isEmpty(), "Delivered animation cues must be released each tick.");
    } finally {
      state.visualSignals.clear();
      state.visualSignals.putAll(previous);
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void coreAndPumpAnimationsDescribeActualWork(GameTestHelper test) {
    var world = test.getLevel();
    int cx = (test.absolutePos(BlockPos.ZERO).getX() >> 4) + 980;
    int cz = (test.absolutePos(BlockPos.ZERO).getZ() >> 4) + 980;
    var state = RestorationState.get(world);
    var previous = new java.util.LinkedHashMap<>(state.visualSignals);
    var pos = new BlockPos(cx * 16 + 8, 200, cz * 16 + 8);
    world.setChunkForced(cx, cz, true);
    world.getChunk(cx, cz);
    var core = new RestorationState.Machine(pos, "restoration_core");
    var pump = new RestorationState.Machine(pos.east(3), "water_restorer");
    try {
      state.visualSignals.clear();
      for (int x = 0; x < 16; x++)
        for (int z = 0; z < 16; z++)
          world.setBlockAndUpdate(
              new BlockPos(cx * 16 + x, 199, cz * 16 + z), Blocks.COARSE_DIRT.defaultBlockState());
      TestChecks.isTrue(test, 
          RestorationEngine.restoreTerrain(world, state, core) == 0
              && state.visualSignals.isEmpty(),
          "A Core without supplies must not pretend to restore soil.");
      core.compost = core.fuel = core.water = 16;
      int changed = RestorationEngine.restoreTerrain(world, state, core);
      var cue = state.visualSignals.get(new AnimationSignals.Key(pos, Kind.CORE));
      TestChecks.isTrue(test, 
          changed > 0 && cue != null && world.getBlockState(cue.target()).is(Blocks.DIRT),
          "Core trails must terminate at a block it actually restored.");
      state.visualSignals.clear();
      core.water = 0;
      pump.water = 256;
      pump.fuel = 8;
      pump.compost = 4;
      pump.setItem(3, new ItemStack(Items.BUCKET));
      state.machines.put(pos.asLong(), core);
      state.machines.put(pump.pos.asLong(), pump);
      MachineLogic.pump(world, pump);
      cue = state.visualSignals.get(new AnimationSignals.Key(pump.pos, Kind.WATER));
      TestChecks.isTrue(test, 
          core.water == 32
              && pump.getItem(6).is(Items.WATER_BUCKET)
              && cue != null
              && cue.target().equals(core.pos),
          "Water trails must identify the receiving machine even when the pump also bottles"
              + " water.");
      state.visualSignals.clear();
      pump.fuel = 0;
      MachineLogic.pump(world, pump);
      TestChecks.isTrue(test, 
          state.visualSignals.isEmpty(), "A stopped pump must not keep a working animation alive.");
    } finally {
      state.machines.remove(pos.asLong());
      state.machines.remove(pump.pos.asLong());
      state.visualSignals.clear();
      state.visualSignals.putAll(previous);
      world.setChunkForced(cx, cz, false);
    }
    test.succeed();
  }
}
