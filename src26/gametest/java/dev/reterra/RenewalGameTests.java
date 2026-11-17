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
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
public class RenewalGameTests {
  private static final String EMPTY_STRUCTURE = "fabric-gametest-api-v1:empty";
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void passiveRecoveryReturnsLocalFlowersInsteadOfRandomSpecies(GameTestHelper test) {
    var world = test.getLevel();
    var p = site(test, 954);
    try {
      for (String biome : java.util.List.of("minecraft:swamp", "minecraft:cherry_grove")) {
        var state = new RestorationState();
        var core = new RestorationState.Machine(p.above(), "restoration_core");
        core.reclaimed = true;
        core.biome = biome;
        core.family = biome.contains("swamp") ? Ecology.Family.SWAMP : Ecology.Family.FOREST;
        state.reclaimedRegions.put(core.pos.asLong(), core);
        int left = (p.getX() >> 4) * 16, top = (p.getZ() >> 4) * 16;
        for (int x = 0; x < 16; x++)
          for (int z = 0; z < 16; z++) {
            world.setBlockAndUpdate(
                new BlockPos(left + x, p.getY(), top + z), Blocks.GRASS_BLOCK.defaultBlockState());
            world.setBlockAndUpdate(
                new BlockPos(left + x, p.getY() + 1, top + z), Blocks.AIR.defaultBlockState());
          }
        for (int i = 0; i < 32; i++) RestorationEngine.rewild(world, state, core);
        int count = 0;
        var expected = biome.contains("swamp") ? Blocks.BLUE_ORCHID : Blocks.PINK_PETALS;
        for (int x = 0; x < 16; x++)
          for (int z = 0; z < 16; z++)
            if (world.getBlockState(new BlockPos(left + x, p.getY() + 1, top + z)).is(expected))
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
    test.succeed();
  }

  private static BlockPos site(GameTestHelper test, int offset) {
    int x = (test.absolutePos(BlockPos.ZERO).getX() >> 4) + offset;
    int z = (test.absolutePos(BlockPos.ZERO).getZ() >> 4) + offset;
    test.getLevel().setChunkForced(x, z, true);
    test.getLevel().getChunk(x, z);
    return new BlockPos(x * 16 + 8, 200, z * 16 + 8);
  }

  private static void release(GameTestHelper test, BlockPos p) {
    test.getLevel().setChunkForced(p.getX() >> 4, p.getZ() >> 4, false);
  }

  private static int columnCursor(RestorationState.Machine core, BlockPos p) {
    for (int i = 0; i < 48 * 48; i++) {
      BlockPos candidate = dev.reterra.world.RecoveryPattern.column(core.pos, 1, i);
      if (candidate.getX() == p.getX() && candidate.getZ() == p.getZ()) return i;
    }
    throw new IllegalArgumentException("Outside core region");
  }

  private static BlockHitResult hit(BlockPos p) {
    return new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false);
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void coreFindsSoilBeneathBranchesTallTrunksAndWater(GameTestHelper test) {
    var world = test.getLevel();
    var p = site(test, 930);
    try {
      var core = new RestorationState.Machine(p.above(), "restoration_core");
      core.registered = true;
      core.compost = core.fuel = core.water = 100;
      var state = new RestorationState();
      BlockPos branch = p.west(3);
      while (Ecology.isScar(branch.getX(), branch.getZ())) branch = branch.east();
      world.setBlockAndUpdate(branch, Blocks.COARSE_DIRT.defaultBlockState());
      world.setBlockAndUpdate(branch.above(30), Blocks.CHERRY_LOG.defaultBlockState());
      TestChecks.isTrue(test, 
          branch.equals(RestorationEngine.ground(world, branch.getX(), branch.getZ())),
          "Air beneath an overhanging branch must not hide the terrain.");
      core.cursor = columnCursor(core, branch);
      RestorationEngine.restoreTerrain(world, state, core);
      TestChecks.isTrue(test, 
          world.getBlockState(branch).is(Blocks.DIRT),
          "A Core must repair beneath an overhanging branch.");
      for (int y = 1; y <= 40; y++)
        world.setBlockAndUpdate(branch.above(y), Blocks.OAK_LOG.defaultBlockState());
      TestChecks.isTrue(test, 
          branch.equals(RestorationEngine.ground(world, branch.getX(), branch.getZ())),
          "A trunk taller than 24 blocks must still expose its ground to the survey.");
      BlockPos bed = p.east(3);
      while (Ecology.isScar(bed.getX(), bed.getZ())) bed = bed.west();
      world.setBlockAndUpdate(bed, Blocks.COARSE_DIRT.defaultBlockState());
      for (int y = 1; y <= 3; y++) world.setBlockAndUpdate(bed.above(y), Blocks.WATER.defaultBlockState());
      core.cursor = columnCursor(core, bed);
      RestorationEngine.restoreTerrain(world, state, core);
      TestChecks.isTrue(test, 
          world.getBlockState(bed).is(Blocks.DIRT)
              && world.getBlockState(bed.above(3)).is(Blocks.WATER),
          "Restoration must repair a shallow bed without removing its water.");
      var sector = new RestorationState.Sector();
      RestorationEngine.sample(world, sector, bed.above(3));
      TestChecks.isTrue(test, 
          sector.water == 1 && sector.fertile == 1,
          "A restored pond column must count both surface water and its fertile bed.");
    } finally {
      release(test, p);
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void coreDoesNotSeedGrassOrConsumeCultures(GameTestHelper test) {
    var world = test.getLevel();
    var p = site(test, 934);
    try {
      world.setBlockAndUpdate(p, Blocks.DIRT.defaultBlockState());
      var core = new RestorationState.Machine(p.above(), "restoration_core");
      core.registered = true;
      core.compost = core.fuel = core.water = core.culture = 128;
      core.setItem(3, new ItemStack(Content.CULTURE, 2));
      for (var sector : core.sectors) {
        sector.lastSampled = sector.lastFertile = 256;
        sector.surveyed = true;
      }
      core.cursor = columnCursor(core, p);
      RestorationEngine.restoreTerrain(world, new RestorationState(), core);
      MachineLogic.stock(core);
      TestChecks.isTrue(test, 
          world.getBlockState(p).is(Blocks.DIRT)
              && core.culture == 128
              && core.getItem(3).getCount() == 2,
          "A supplied Core must leave grass planting and cultures to the player.");
      var player = test.makeMockServerPlayerInLevel();
      var handler =
          new dev.reterra.screen.MachineScreenHandler(1, player.getInventory(), world, core, false);
      TestChecks.isFalse(test, 
          handler.getSlot(3).mayPlace(new ItemStack(Content.CULTURE)),
          "Cores must not accept new cultures.");
      MachineLogic.refundCultures(world, core);
      int returned = core.getItem(3).getCount();
      MachineLogic.refundCultures(world, core);
      TestChecks.isTrue(test, 
          core.culture == 0
              && returned > 2
              && core.getItem(3).getCount() == returned
              && handler.supplyVisible(3)
              && handler.getSlot(3).mayPickup(player),
          "Old culture credits must be refunded once in a removable slot.");
    } finally {
      release(test, p);
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void handPlantingSeedsAPatchAndReturnsAReusableBottle(GameTestHelper test) {
    var world = test.getLevel();
    var p = site(test, 938);
    try {
      for (int x = -2; x <= 2; x++)
        for (int z = -2; z <= 2; z++) {
          world.setBlockAndUpdate(p.offset(x, 0, z), Blocks.DIRT.defaultBlockState());
          world.setBlockAndUpdate(p.offset(x, 1, z), Blocks.AIR.defaultBlockState());
        }
      var connected = test.makeMockServerPlayerInLevel();
      // The vanilla GameTest helper overrides isCreative() to always return true.
      var player =
          new net.minecraft.server.level.ServerPlayer(
              world.getServer(),
              world,
              new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "survival-gardener"),
              net.minecraft.server.level.ClientInformation.createDefault());
      player.connection = connected.connection;
      player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
      player.getInventory().clearContent();
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Content.CULTURE));
      var planted = MachineInteraction.use(player, world, InteractionHand.MAIN_HAND, hit(p));
      int grass = 0, bottles = 0;
      for (int x = -2; x <= 2; x++)
        for (int z = -2; z <= 2; z++)
          if (world.getBlockState(p.offset(x, 0, z)).is(Blocks.GRASS_BLOCK)) grass++;
      for (var stack : java.util.stream.IntStream.range(0, 36).mapToObj(player.getInventory()::getItem).toList())
        if (stack.is(Items.GLASS_BOTTLE)) bottles += stack.getCount();
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
              + player.mayBuild()
              + ", world="
              + (dev.reterra.compat.WorldAccess.world(player) == world));
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
      MachineInteraction.use(player, world, InteractionHand.MAIN_HAND, hit(p));
      TestChecks.isTrue(test, 
          player.getInventory().contains(new ItemStack(Content.CULTURE))
              && world.getBlockState(p).is(Blocks.GRASS_BLOCK),
          "The returned bottle must collect more culture without destroying grass.");
    } finally {
      release(test, p);
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void localPlantsAreDefinedAndCultivatedForTheOriginalBiome(GameTestHelper test) {
    var world = test.getLevel();
    var p = site(test, 942);
    var state = RestorationState.get(world);
    var core = new RestorationState.Machine(p.east(5), "restoration_core");
    core.registered = true;
    state.machines.put(core.pos.asLong(), core);
    try {
      TestChecks.isFalse(test, 
          HabitatPlants.nativePlant(
              Ecology.Family.SWAMP, "minecraft:swamp", Blocks.ROSE_BUSH.defaultBlockState()),
          "A rose bush must not award swamp-native care.");
      TestChecks.isTrue(test, 
          HabitatPlants.nativePlant(
                  Ecology.Family.SWAMP, "minecraft:swamp", Blocks.BLUE_ORCHID.defaultBlockState())
              && HabitatPlants.nativePlant(
                  Ecology.Family.SWAMP, "minecraft:swamp", Blocks.SUGAR_CANE.defaultBlockState()),
          "Swamps must define orchids and reeds as local vegetation.");
      var player = test.makeMockServerPlayerInLevel();
      for (String biome : java.util.List.of("minecraft:swamp", "minecraft:cherry_grove")) {
        core.biome = biome;
        core.family = biome.contains("swamp") ? Ecology.Family.SWAMP : Ecology.Family.FOREST;
        for (int x = -2; x <= 2; x++)
          for (int z = -2; z <= 2; z++) {
            world.setBlockAndUpdate(p.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState());
            world.setBlockAndUpdate(p.offset(x, 1, z), Blocks.AIR.defaultBlockState());
          }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Content.COMPOST));
        MachineInteraction.use(player, world, InteractionHand.MAIN_HAND, hit(p));
        int count = 0;
        var expected = biome.contains("swamp") ? Blocks.BLUE_ORCHID : Blocks.PINK_PETALS;
        for (int x = -2; x <= 2; x++)
          for (int z = -2; z <= 2; z++)
            if (world.getBlockState(p.offset(x, 1, z)).is(expected)) count++;
        TestChecks.isTrue(test, 
            count == 5, "Cultivated flowers must follow the original " + biome + " palette.");
      }
    } finally {
      state.machines.remove(core.pos.asLong());
      release(test, p);
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void branchedCherryCrownsRegrowInThreeDimensionsAndResumeAfterSaving(GameTestHelper test) {
    var world = test.getLevel();
    var p = site(test, 946);
    try {
      var state = new RestorationState();
      var core = new RestorationState.Machine(p, "restoration_core");
      core.reclaimed = true;
      core.biome = "minecraft:cherry_grove";
      state.reclaimedRegions.put(core.pos.asLong(), core);
      for (int y = 1; y <= 6; y++)
        world.setBlockAndUpdate(p.above(y), Blocks.CHERRY_LOG.defaultBlockState());
      for (int x = 1; x <= 3; x++)
        world.setBlockAndUpdate(p.offset(x, 5, 0), Blocks.CHERRY_LOG.defaultBlockState());
      var protectedPos = p.offset(3, 6, 1);
      world.setBlockAndUpdate(protectedPos, Blocks.GLASS.defaultBlockState());
      state.protectedBlocks.add(protectedPos.asLong());
      TreeRecovery.enqueueColumn(world, state, core, p.above());
      TreeRecovery.tick(world, state);
      TestChecks.isTrue(test, 
          !state.treeJobs.isEmpty(), "A crown should grow gradually over several bounded jobs.");
      var saved =
          RestorationState.fromNbt(
              state.writeNbt(new net.minecraft.nbt.CompoundTag(), world.registryAccess()),
              world.registryAccess());
      TestChecks.isTrue(test, 
          saved.treeJobs.size() == 1 && saved.treeJobs.values().iterator().next().cursor > 0,
          "Canopy work must keep its cursor across saves.");
      for (int i = 0; i < 80; i++) TreeRecovery.tick(world, saved);
      int leaves = 0;
      java.util.Set<Integer> levels = new java.util.HashSet<>();
      for (int x = -4; x <= 6; x++)
        for (int z = -4; z <= 4; z++)
          for (int y = 1; y <= 10; y++)
            if (world.getBlockState(p.offset(x, y, z)).is(Blocks.CHERRY_LEAVES)) {
              leaves++;
              levels.add(y);
            }
      TestChecks.isTrue(test, 
          leaves > 50 && levels.size() >= 3 && saved.treeJobs.isEmpty(),
          "A cherry's branch tips need a complete thick canopy, rather than an eight-leaf flat"
              + " patch.");
      TestChecks.isTrue(test, 
          world.getBlockState(protectedPos).is(Blocks.GLASS),
          "Regrowth must preserve player construction.");
      for (int y = 1; y <= 4; y++)
        world.setBlockAndUpdate(p.west(3).above(y), Blocks.OAK_LOG.defaultBlockState());
      saved.protectedBlocks.add(p.west(3).above().asLong());
      TreeRecovery.enqueueColumn(world, saved, core, p.west(3).above());
      TestChecks.isTrue(test, saved.treeJobs.isEmpty(), "Player log pillars must not receive canopies.");
      BlockPos removed = p.south(3).above();
      for (int y = 0; y < 5; y++)
        world.setBlockAndUpdate(removed.above(y), Blocks.SPRUCE_LOG.defaultBlockState());
      TreeRecovery.enqueueColumn(world, saved, core, removed);
      TestChecks.isTrue(test, 
          !saved.treeJobs.isEmpty(), "A second natural trunk should begin a recovery job.");
      world.setBlockAndUpdate(removed, Blocks.AIR.defaultBlockState());
      TreeRecovery.tick(world, saved);
      TestChecks.isTrue(test, 
          saved.treeJobs.isEmpty(),
          "Removing the trunk must cancel its pending canopy instead of creating floating leaves.");
    } finally {
      release(test, p);
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void heartAnimationPersistsAndTheDisableCommandAllowsAReplay(GameTestHelper test) {
    var world = test.getLevel();
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
      world.setBlockAndUpdate(p, Content.HEART.defaultBlockState());
      for (var part : Multiblocks.parts("heart_of_the_world"))
        world.setBlockAndUpdate(p.offset(part.x(), part.y(), part.z()), part.block().defaultBlockState());
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
              state.writeNbt(new net.minecraft.nbt.CompoundTag(), world.registryAccess()),
              world.registryAccess());
      TestChecks.isTrue(test, 
          copy.heartAwakening == state.heartAwakening
              && copy.heartCrystal.equals(state.heartCrystal),
          "Saving must preserve sequence timing and crystal ownership.");
      int age = state.heartAwakening;
      world.setBlockAndUpdate(p.east(), Blocks.AIR.defaultBlockState());
      HeartAwakening.tick(world, state);
      TestChecks.isTrue(test, state.heartAwakening == age, "Missing supports must pause the awakening.");
      world.setBlockAndUpdate(p.east(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
      TestChecks.isTrue(test, 
          Multiblocks.issue(world, p, "heart_of_the_world") == 0,
          "Chiseled stone brick supports must complete the Heart assembly without moss.");
      var crystal = state.heartCrystalHandle;
      var player = test.makeMockServerPlayerInLevel();
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
              && world.getBlockState(p).getValue(dev.reterra.machine.MachineBlock.LIT),
          "The finale must finish with a lit Heart and no temporary crystal.");
      TestAccess.execute(TestAccess.gamemasterSource(player), "reterra heart disable");
      TestChecks.isTrue(test, 
          !state.heartActivated
              && !world.getBlockState(p).getValue(dev.reterra.machine.MachineBlock.LIT),
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
    test.succeed();
  }
}
