package dev.reterra;

import dev.reterra.ecology.Ecology;
import dev.reterra.ecology.HabitatPlants;
import dev.reterra.machine.MachineInteraction;
import dev.reterra.machine.MachineLogic;
import dev.reterra.machine.Multiblocks;
import dev.reterra.machine.RestorationEngine;
import dev.reterra.state.RestorationState;
import dev.reterra.world.HeartAwakening;
import dev.reterra.world.TreeRecovery;
//? if <1.21.5 {
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
//? }
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
//? if <1.21.5 {
import net.minecraft.test.GameTest;
//? } else {
/*import net.fabricmc.fabric.api.gametest.v1.GameTest;
*///? }
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

//? if <1.21.5 {
public class RenewalGameTests implements FabricGameTest {
//? } else {
/*public class RenewalGameTests {
  private static final String EMPTY_STRUCTURE = "fabric-gametest-api-v1:empty";
*///? }
  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void passiveRecoveryReturnsLocalFlowersInsteadOfRandomSpecies(TestContext test) {
    var world = test.getWorld();
    var p = site(test, 954);
    try {
      for (String biome : java.util.List.of("minecraft:swamp", "minecraft:cherry_grove")) {
        var state = new RestorationState();
        var core = new RestorationState.Machine(p.up(), "restoration_core");
        core.reclaimed = true;
        core.biome = biome;
        core.family = biome.contains("swamp") ? Ecology.Family.SWAMP : Ecology.Family.FOREST;
        state.reclaimedRegions.put(core.pos.asLong(), core);
        int left = (p.getX() >> 4) * 16, top = (p.getZ() >> 4) * 16;
        for (int x = 0; x < 16; x++)
          for (int z = 0; z < 16; z++) {
            world.setBlockState(
                new BlockPos(left + x, p.getY(), top + z), Blocks.GRASS_BLOCK.getDefaultState());
            world.setBlockState(
                new BlockPos(left + x, p.getY() + 1, top + z), Blocks.AIR.getDefaultState());
          }
        for (int i = 0; i < 32; i++) RestorationEngine.rewild(world, state, core);
        int count = 0;
        var expected = biome.contains("swamp") ? Blocks.BLUE_ORCHID : Blocks.PINK_PETALS;
        for (int x = 0; x < 16; x++)
          for (int z = 0; z < 16; z++)
            if (world.getBlockState(new BlockPos(left + x, p.getY() + 1, top + z)).isOf(expected))
              count++;
        TestChecks.isTrue(test, 
            count > 0,
            "Passive recovery must return "
                + expected.getName().getString()
                + " in "
                + biome
                + ".");
      }
    } finally {
      release(test, p);
    }
    test.complete();
  }

  private static BlockPos site(TestContext test, int offset) {
    int x = (test.getAbsolutePos(BlockPos.ORIGIN).getX() >> 4) + offset;
    int z = (test.getAbsolutePos(BlockPos.ORIGIN).getZ() >> 4) + offset;
    test.getWorld().setChunkForced(x, z, true);
    test.getWorld().getChunk(x, z);
    return new BlockPos(x * 16 + 8, 200, z * 16 + 8);
  }

  private static void release(TestContext test, BlockPos p) {
    test.getWorld().setChunkForced(p.getX() >> 4, p.getZ() >> 4, false);
  }

  private static int columnCursor(RestorationState.Machine core, BlockPos p) {
    for (int i = 0; i < 48 * 48; i++) {
      BlockPos candidate = dev.reterra.world.RecoveryPattern.column(core.pos, 1, i);
      if (candidate.getX() == p.getX() && candidate.getZ() == p.getZ()) return i;
    }
    throw new IllegalArgumentException("Outside core region");
  }

  private static BlockHitResult hit(BlockPos p) {
    return new BlockHitResult(Vec3d.ofCenter(p), Direction.UP, p, false);
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void coreFindsSoilBeneathBranchesTallTrunksAndWater(TestContext test) {
    var world = test.getWorld();
    var p = site(test, 930);
    try {
      var core = new RestorationState.Machine(p.up(), "restoration_core");
      core.registered = true;
      core.compost = core.fuel = core.water = 100;
      var state = new RestorationState();
      BlockPos branch = p.west(3);
      while (Ecology.isScar(branch.getX(), branch.getZ())) branch = branch.east();
      world.setBlockState(branch, Blocks.COARSE_DIRT.getDefaultState());
      world.setBlockState(branch.up(30), Blocks.CHERRY_LOG.getDefaultState());
      TestChecks.isTrue(test, 
          branch.equals(RestorationEngine.ground(world, branch.getX(), branch.getZ())),
          "Air beneath an overhanging branch must not hide the terrain.");
      core.cursor = columnCursor(core, branch);
      RestorationEngine.restoreTerrain(world, state, core);
      TestChecks.isTrue(test, 
          world.getBlockState(branch).isOf(Blocks.DIRT),
          "A Core must repair beneath an overhanging branch.");
      for (int y = 1; y <= 40; y++)
        world.setBlockState(branch.up(y), Blocks.OAK_LOG.getDefaultState());
      TestChecks.isTrue(test, 
          branch.equals(RestorationEngine.ground(world, branch.getX(), branch.getZ())),
          "A trunk taller than 24 blocks must still expose its ground to the survey.");
      BlockPos bed = p.east(3);
      while (Ecology.isScar(bed.getX(), bed.getZ())) bed = bed.west();
      world.setBlockState(bed, Blocks.COARSE_DIRT.getDefaultState());
      for (int y = 1; y <= 3; y++) world.setBlockState(bed.up(y), Blocks.WATER.getDefaultState());
      core.cursor = columnCursor(core, bed);
      RestorationEngine.restoreTerrain(world, state, core);
      TestChecks.isTrue(test, 
          world.getBlockState(bed).isOf(Blocks.DIRT)
              && world.getBlockState(bed.up(3)).isOf(Blocks.WATER),
          "Restoration must repair a shallow bed without removing its water.");
      var sector = new RestorationState.Sector();
      RestorationEngine.sample(world, sector, bed.up(3));
      TestChecks.isTrue(test, 
          sector.water == 1 && sector.fertile == 1,
          "A restored pond column must count both surface water and its fertile bed.");
    } finally {
      release(test, p);
    }
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void coreDoesNotSeedGrassOrConsumeCultures(TestContext test) {
    var world = test.getWorld();
    var p = site(test, 934);
    try {
      world.setBlockState(p, Blocks.DIRT.getDefaultState());
      var core = new RestorationState.Machine(p.up(), "restoration_core");
      core.registered = true;
      core.compost = core.fuel = core.water = core.culture = 128;
      core.setStack(3, new ItemStack(Content.CULTURE, 2));
      for (var sector : core.sectors) {
        sector.lastSampled = sector.lastFertile = 256;
        sector.surveyed = true;
      }
      core.cursor = columnCursor(core, p);
      RestorationEngine.restoreTerrain(world, new RestorationState(), core);
      MachineLogic.stock(core);
      TestChecks.isTrue(test, 
          world.getBlockState(p).isOf(Blocks.DIRT)
              && core.culture == 128
              && core.getStack(3).getCount() == 2,
          "A supplied Core must leave grass planting and cultures to the player.");
      var player = test.createMockCreativeServerPlayerInWorld();
      var handler =
          new dev.reterra.screen.MachineScreenHandler(1, player.getInventory(), world, core, false);
      TestChecks.isFalse(test, 
          handler.getSlot(3).canInsert(new ItemStack(Content.CULTURE)),
          "Cores must not accept new cultures.");
      MachineLogic.refundCultures(world, core);
      int returned = core.getStack(3).getCount();
      MachineLogic.refundCultures(world, core);
      TestChecks.isTrue(test, 
          core.culture == 0
              && returned > 2
              && core.getStack(3).getCount() == returned
              && handler.supplyVisible(3)
              && handler.getSlot(3).canTakeItems(player),
          "Old culture credits must be refunded once in a removable slot.");
    } finally {
      release(test, p);
    }
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void handPlantingSeedsAPatchAndReturnsAReusableBottle(TestContext test) {
    var world = test.getWorld();
    var p = site(test, 938);
    try {
      for (int x = -2; x <= 2; x++)
        for (int z = -2; z <= 2; z++) {
          world.setBlockState(p.add(x, 0, z), Blocks.DIRT.getDefaultState());
          world.setBlockState(p.add(x, 1, z), Blocks.AIR.getDefaultState());
        }
      var connected = test.createMockCreativeServerPlayerInWorld();
      // The vanilla GameTest helper overrides isCreative() to always return true.
      var player =
          new net.minecraft.server.network.ServerPlayerEntity(
              world.getServer(),
              world,
              new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "survival-gardener"),
              net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());
      player.networkHandler = connected.networkHandler;
      player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
      player.getInventory().clear();
      player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Content.CULTURE));
      var planted = MachineInteraction.use(player, world, Hand.MAIN_HAND, hit(p));
      int grass = 0, bottles = 0;
      for (int x = -2; x <= 2; x++)
        for (int z = -2; z <= 2; z++)
          if (world.getBlockState(p.add(x, 0, z)).isOf(Blocks.GRASS_BLOCK)) grass++;
      for (var stack : java.util.stream.IntStream.range(0, 36).mapToObj(player.getInventory()::getStack).toList())
        if (stack.isOf(Items.GLASS_BOTTLE)) bottles += stack.getCount();
      TestChecks.isTrue(test, 
          grass == 25 && bottles == 1,
          "One culture must seed a 5 x 5 patch and return exactly one bottle: grass="
              + grass
              + ", bottles="
              + bottles
              + ", result="
              + planted
              + ", creative="
              + player.isCreative()
              + ", modify="
              + player.canModifyBlocks()
              + ", world="
              + (dev.reterra.compat.WorldAccess.world(player) == world));
      player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
      MachineInteraction.use(player, world, Hand.MAIN_HAND, hit(p));
      TestChecks.isTrue(test, 
          player.getInventory().contains(new ItemStack(Content.CULTURE))
              && world.getBlockState(p).isOf(Blocks.GRASS_BLOCK),
          "The returned bottle must collect more culture without destroying grass.");
    } finally {
      release(test, p);
    }
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void localPlantsAreDefinedAndCultivatedForTheOriginalBiome(TestContext test) {
    var world = test.getWorld();
    var p = site(test, 942);
    var state = RestorationState.get(world);
    var core = new RestorationState.Machine(p.east(5), "restoration_core");
    core.registered = true;
    state.machines.put(core.pos.asLong(), core);
    try {
      TestChecks.isFalse(test, 
          HabitatPlants.nativePlant(
              Ecology.Family.SWAMP, "minecraft:swamp", Blocks.ROSE_BUSH.getDefaultState()),
          "A rose bush must not award swamp-native care.");
      TestChecks.isTrue(test, 
          HabitatPlants.nativePlant(
                  Ecology.Family.SWAMP, "minecraft:swamp", Blocks.BLUE_ORCHID.getDefaultState())
              && HabitatPlants.nativePlant(
                  Ecology.Family.SWAMP, "minecraft:swamp", Blocks.SUGAR_CANE.getDefaultState()),
          "Swamps must define orchids and reeds as local vegetation.");
      var player = test.createMockCreativeServerPlayerInWorld();
      for (String biome : java.util.List.of("minecraft:swamp", "minecraft:cherry_grove")) {
        core.biome = biome;
        core.family = biome.contains("swamp") ? Ecology.Family.SWAMP : Ecology.Family.FOREST;
        for (int x = -2; x <= 2; x++)
          for (int z = -2; z <= 2; z++) {
            world.setBlockState(p.add(x, 0, z), Blocks.GRASS_BLOCK.getDefaultState());
            world.setBlockState(p.add(x, 1, z), Blocks.AIR.getDefaultState());
          }
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Content.COMPOST));
        MachineInteraction.use(player, world, Hand.MAIN_HAND, hit(p));
        int count = 0;
        var expected = biome.contains("swamp") ? Blocks.BLUE_ORCHID : Blocks.PINK_PETALS;
        for (int x = -2; x <= 2; x++)
          for (int z = -2; z <= 2; z++)
            if (world.getBlockState(p.add(x, 1, z)).isOf(expected)) count++;
        TestChecks.isTrue(test, 
            count == 5, "Cultivated flowers must follow the original " + biome + " palette.");
      }
    } finally {
      state.machines.remove(core.pos.asLong());
      release(test, p);
    }
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void branchedCherryCrownsRegrowInThreeDimensionsAndResumeAfterSaving(TestContext test) {
    var world = test.getWorld();
    var p = site(test, 946);
    try {
      var state = new RestorationState();
      var core = new RestorationState.Machine(p, "restoration_core");
      core.reclaimed = true;
      core.biome = "minecraft:cherry_grove";
      state.reclaimedRegions.put(core.pos.asLong(), core);
      for (int y = 1; y <= 6; y++)
        world.setBlockState(p.up(y), Blocks.CHERRY_LOG.getDefaultState());
      for (int x = 1; x <= 3; x++)
        world.setBlockState(p.add(x, 5, 0), Blocks.CHERRY_LOG.getDefaultState());
      var protectedPos = p.add(3, 6, 1);
      world.setBlockState(protectedPos, Blocks.GLASS.getDefaultState());
      state.protectedBlocks.add(protectedPos.asLong());
      TreeRecovery.enqueueColumn(world, state, core, p.up());
      TreeRecovery.tick(world, state);
      TestChecks.isTrue(test, 
          !state.treeJobs.isEmpty(), "A crown should grow gradually over several bounded jobs.");
      var saved =
          RestorationState.fromNbt(
              state.writeNbt(new net.minecraft.nbt.NbtCompound(), world.getRegistryManager()),
              world.getRegistryManager());
      TestChecks.isTrue(test, 
          saved.treeJobs.size() == 1 && saved.treeJobs.values().iterator().next().cursor > 0,
          "Canopy work must keep its cursor across saves.");
      for (int i = 0; i < 80; i++) TreeRecovery.tick(world, saved);
      int leaves = 0;
      java.util.Set<Integer> levels = new java.util.HashSet<>();
      for (int x = -4; x <= 6; x++)
        for (int z = -4; z <= 4; z++)
          for (int y = 1; y <= 10; y++)
            if (world.getBlockState(p.add(x, y, z)).isOf(Blocks.CHERRY_LEAVES)) {
              leaves++;
              levels.add(y);
            }
      TestChecks.isTrue(test, 
          leaves > 50 && levels.size() >= 3 && saved.treeJobs.isEmpty(),
          "A cherry's branch tips need a complete thick canopy, rather than an eight-leaf flat"
              + " patch.");
      TestChecks.isTrue(test, 
          world.getBlockState(protectedPos).isOf(Blocks.GLASS),
          "Regrowth must preserve player construction.");
      for (int y = 1; y <= 4; y++)
        world.setBlockState(p.west(3).up(y), Blocks.OAK_LOG.getDefaultState());
      saved.protectedBlocks.add(p.west(3).up().asLong());
      TreeRecovery.enqueueColumn(world, saved, core, p.west(3).up());
      TestChecks.isTrue(test, saved.treeJobs.isEmpty(), "Player log pillars must not receive canopies.");
      BlockPos removed = p.south(3).up();
      for (int y = 0; y < 5; y++)
        world.setBlockState(removed.up(y), Blocks.SPRUCE_LOG.getDefaultState());
      TreeRecovery.enqueueColumn(world, saved, core, removed);
      TestChecks.isTrue(test, 
          !saved.treeJobs.isEmpty(), "A second natural trunk should begin a recovery job.");
      world.setBlockState(removed, Blocks.AIR.getDefaultState());
      TreeRecovery.tick(world, saved);
      TestChecks.isTrue(test, 
          saved.treeJobs.isEmpty(),
          "Removing the trunk must cancel its pending canopy instead of creating floating leaves.");
    } finally {
      release(test, p);
    }
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void heartAnimationPersistsAndTheDisableCommandAllowsAReplay(TestContext test) {
    var world = test.getWorld();
    var p = site(test, 950);
    var state = RestorationState.get(world);
    var completed = java.util.EnumSet.noneOf(Ecology.Family.class);
    completed.addAll(state.completed);
    boolean oldActive = state.heartActivated;
    BlockPos oldPos = state.heartPos;
    int oldAge = state.heartAwakening, oldTicks = state.heartTicks, oldCursor = state.heartCursor;
    var oldCrystal = state.heartCrystal;
    try {
      state.heartActivated = false;
      state.heartAwakening = 0;
      state.heartCrystal = null;
      state.completed.addAll(java.util.EnumSet.allOf(Ecology.Family.class));
      world.setBlockState(p, Content.HEART.getDefaultState());
      for (var part : Multiblocks.parts("heart_of_the_world"))
        world.setBlockState(p.add(part.x(), part.y(), part.z()), part.block().getDefaultState());
      var heart = new RestorationState.Machine(p, "heart_of_the_world");
      state.machines.put(p.asLong(), heart);
      TestChecks.isTrue(test, 
          HeartAwakening.start(world, heart) && !state.heartActivated,
          "Awakening must be a sequence, not instant activation.");
      for (int i = 0; i < 25; i++) HeartAwakening.tick(world, state);
      TestChecks.isTrue(test, 
          state.heartCrystal != null
              && state.heartCrystalHandle != null
              && !state.heartCrystalHandle.isRemoved(),
          "The sequence must spawn its animated crystal.");
      var copy =
          RestorationState.fromNbt(
              state.writeNbt(new net.minecraft.nbt.NbtCompound(), world.getRegistryManager()),
              world.getRegistryManager());
      TestChecks.isTrue(test, 
          copy.heartAwakening == state.heartAwakening
              && copy.heartCrystal.equals(state.heartCrystal),
          "Saving must preserve sequence timing and crystal ownership.");
      int age = state.heartAwakening;
      world.setBlockState(p.east(), Blocks.AIR.getDefaultState());
      HeartAwakening.tick(world, state);
      TestChecks.isTrue(test, state.heartAwakening == age, "Missing supports must pause the awakening.");
      world.setBlockState(p.east(), Blocks.CHISELED_STONE_BRICKS.getDefaultState());
      TestChecks.isTrue(test, 
          Multiblocks.issue(world, p, "heart_of_the_world") == 0,
          "Chiseled stone brick supports must complete the Heart assembly without moss.");
      var crystal = state.heartCrystalHandle;
      var player = test.createMockCreativeServerPlayerInWorld();
      TestAccess.execute(TestAccess.gamemasterSource(player), "reterra heart disable");
      TestChecks.isTrue(test, 
          !state.heartActivated
              && state.heartAwakening == 0
              && state.heartCrystal == null
              && crystal.isRemoved()
              && state.completed.size() == 4,
          "Disabling must cancel the sequence and remove its crystal without clearing ecosystems.");
      TestChecks.isTrue(test, 
          HeartAwakening.start(world, heart), "The same Heart must be available for another test.");
      for (int i = 0; i < 64; i++) HeartAwakening.tick(world, state);
      TestChecks.isTrue(test, 
          state.heartActivated
              && state.heartAwakening == 0
              && state.heartCrystal == null
              && world.getBlockState(p).get(dev.reterra.machine.MachineBlock.LIT),
          "The finale must finish with a lit Heart and no temporary crystal.");
      TestAccess.execute(TestAccess.gamemasterSource(player), "reterra heart disable");
      TestChecks.isTrue(test, 
          !state.heartActivated
              && !world.getBlockState(p).get(dev.reterra.machine.MachineBlock.LIT),
          "The testing command must also disable a fully awakened Heart.");
    } finally {
      HeartAwakening.disable(world);
      state.machines.remove(p.asLong());
      state.completed.clear();
      state.completed.addAll(completed);
      state.heartActivated = oldActive;
      state.heartPos = oldPos;
      state.heartAwakening = oldAge;
      state.heartTicks = oldTicks;
      state.heartCursor = oldCursor;
      state.heartCrystal = oldCrystal;
      release(test, p);
    }
    test.complete();
  }
}
