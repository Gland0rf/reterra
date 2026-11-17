package dev.reterra;

import dev.reterra.effect.AnimationPayload.Kind;
import dev.reterra.effect.AnimationSignals;
import dev.reterra.machine.MachineLogic;
import dev.reterra.machine.RestorationEngine;
import dev.reterra.state.RestorationState;
//? if <1.21.5 {
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
//? }
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
//? if <1.21.5 {
import net.minecraft.test.GameTest;
//? } else {
/*import net.fabricmc.fabric.api.gametest.v1.GameTest;
*///? }
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

//? if <1.21.5 {
public class AnimationGameTests implements FabricGameTest {
//? } else {
/*public class AnimationGameTests {
  private static final String EMPTY_STRUCTURE = "fabric-gametest-api-v1:empty";
*///? }
  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void pumpStatusRecognizesPondAndCauldronIntakes(TestContext test) {
    var world = test.getWorld();
    var pos = test.getAbsolutePos(new BlockPos(3, 12, 3));
    var pump = new RestorationState.Machine(pos, "water_restorer");
    world.setBlockState(pos, Content.WATERWORKS.getDefaultState());
    world.setBlockState(pos.down(), Blocks.COPPER_BLOCK.getDefaultState());
    world.setBlockState(pos.north(), Blocks.WATER_CAULDRON.getDefaultState());
    pump.compost = 8;
    pump.fuel = 128;
    world.setBlockState(pos.east(2), Blocks.WATER.getDefaultState());
    TestChecks.isTrue(test, 
        MachineLogic.status(world, pump) == MachineLogic.Status.READY,
        "A pond-fed pump must not ask for water just because its internal tank is empty.");
    dev.reterra.machine.MachineBlock.updateVisual(world, pump);
    TestChecks.isTrue(test, 
        world.getBlockState(pos).get(dev.reterra.machine.MachineBlock.LIT),
        "A fueled and filtered pond-fed pump must show its working indicator.");
    world.setBlockState(pos.east(2), Blocks.AIR.getDefaultState());
    world.setBlockState(
        pos.north(),
        Blocks.WATER_CAULDRON
            .getDefaultState()
            .with(net.minecraft.block.LeveledCauldronBlock.LEVEL, 3));
    TestChecks.isTrue(test, 
        MachineLogic.status(world, pump) == MachineLogic.Status.READY,
        "A full intake cauldron is also a usable water supply.");
    world.setBlockState(pos.north(), Blocks.WATER_CAULDRON.getDefaultState());
    dev.reterra.machine.MachineBlock.updateVisual(world, pump);
    TestChecks.isTrue(test, 
        MachineLogic.status(world, pump) == MachineLogic.Status.WATER
            && !world.getBlockState(pos).get(dev.reterra.machine.MachineBlock.LIT),
        "Without stored water, a pond or a full cauldron, the pump must request water.");
    world.setBlockState(pos.east(2), Blocks.WATER.getDefaultState());
    pump.paused = true;
    dev.reterra.machine.MachineBlock.updateVisual(world, pump);
    TestChecks.isTrue(test, 
        MachineLogic.status(world, pump) == MachineLogic.Status.PAUSED
            && !world.getBlockState(pos).get(dev.reterra.machine.MachineBlock.LIT),
        "An available intake must not override the pause control.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void animationCuesAreBoundedTransientAndCanceledWithTheHeart(TestContext test) {
    var world = test.getWorld();
    var state = RestorationState.get(world);
    var previous = new java.util.LinkedHashMap<>(state.visualSignals);
    try {
      state.visualSignals.clear();
      var origin = test.getAbsolutePos(BlockPos.ORIGIN);
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
              state.writeNbt(new NbtCompound(), world.getRegistryManager()),
              world.getRegistryManager());
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
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void coreAndPumpAnimationsDescribeActualWork(TestContext test) {
    var world = test.getWorld();
    int cx = (test.getAbsolutePos(BlockPos.ORIGIN).getX() >> 4) + 980;
    int cz = (test.getAbsolutePos(BlockPos.ORIGIN).getZ() >> 4) + 980;
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
          world.setBlockState(
              new BlockPos(cx * 16 + x, 199, cz * 16 + z), Blocks.COARSE_DIRT.getDefaultState());
      TestChecks.isTrue(test, 
          RestorationEngine.restoreTerrain(world, state, core) == 0
              && state.visualSignals.isEmpty(),
          "A Core without supplies must not pretend to restore soil.");
      core.compost = core.fuel = core.water = 16;
      int changed = RestorationEngine.restoreTerrain(world, state, core);
      var cue = state.visualSignals.get(new AnimationSignals.Key(pos, Kind.CORE));
      TestChecks.isTrue(test, 
          changed > 0 && cue != null && world.getBlockState(cue.target()).isOf(Blocks.DIRT),
          "Core trails must terminate at a block it actually restored.");
      state.visualSignals.clear();
      core.water = 0;
      pump.water = 256;
      pump.fuel = 8;
      pump.compost = 4;
      pump.setStack(3, new ItemStack(Items.BUCKET));
      state.machines.put(pos.asLong(), core);
      state.machines.put(pump.pos.asLong(), pump);
      MachineLogic.pump(world, pump);
      cue = state.visualSignals.get(new AnimationSignals.Key(pump.pos, Kind.WATER));
      TestChecks.isTrue(test, 
          core.water == 32
              && pump.getStack(6).isOf(Items.WATER_BUCKET)
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
    test.complete();
  }
}
