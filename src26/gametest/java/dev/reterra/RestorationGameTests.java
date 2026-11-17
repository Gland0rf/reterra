package dev.reterra;

import dev.reterra.ecology.Ecology;
import dev.reterra.machine.Multiblocks;
import dev.reterra.state.RestorationState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
public class RestorationGameTests {
  private static final String EMPTY_STRUCTURE = "fabric-gametest-api-v1:empty";
  @net.fabricmc.fabric.api.gametest.v1.GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void legacyTerrainChoiceSurvivesGameRuleMigration(GameTestHelper test) {
    for (boolean barren : new boolean[] {false, true}) {
      var level = new CompoundTag();
      var rules = new CompoundTag();
      rules.putString("reterraBarrenWorld", Boolean.toString(barren));
      rules.putString("keepInventory", "true");
      level.put("GameRules", rules);
      var fixed = net.minecraft.util.datafix.DataFixTypes.LEVEL.updateToCurrentVersion(
          test.getLevel().getServer().getFixerUpper(), level, 3953);
      var migrated = fixed.getCompoundOrEmpty("game_rules");
      TestChecks.isTrue(test, migrated.getBooleanOr("reterra:barren_world", !barren) == barren,
          "Upgrading must preserve the saved terrain choice.");
      TestChecks.isTrue(test, migrated.getBooleanOr("minecraft:keep_inventory", false),
          "The ReTerra migration must preserve vanilla game rules too.");
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void earlyMaterialsRevealTheWholeMachineRecipeChain(GameTestHelper test) {
    var player = test.makeMockServerPlayerInLevel();
    var iron = new ItemStack(Items.IRON_INGOT);
    player.getInventory().setItem(0, iron);
    TestAccess.inventoryChanged(player, iron);
    for (String recipe :
        java.util.List.of(
            "soil_reclaimer",
            "restoration_core",
            "revival_chamber",
            "archive_fragment",
            "environmental_scanner"))
      TestChecks.isTrue(test, 
          TestAccess.unlocked(player, recipe),
          "Obtaining iron must reveal the "
              + recipe
              + " recipe before its late ingredients are found.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void tanksAcceptASecondBucketAndNeverOverflow(GameTestHelper test) {
    for (String kind : dev.reterra.machine.MachineLogic.KINDS.subList(0, 4)) {
      var machine = new RestorationState.Machine(BlockPos.ZERO, kind);
      machine.water = 128;
      machine.setItem(2, new ItemStack(Items.WATER_BUCKET));
      dev.reterra.machine.MachineLogic.stock(machine);
      TestChecks.isTrue(test, 
          machine.water == 256 && machine.getItem(2).is(Items.BUCKET),
          "A half-full " + kind + " must accept a second bucket in the same slot.");
      machine.setItem(2, new ItemStack(Items.WATER_BUCKET));
      dev.reterra.machine.MachineLogic.stock(machine);
      TestChecks.isTrue(test, 
          machine.water == 256 && machine.getItem(2).is(Items.WATER_BUCKET),
          "Full tanks must leave the next water bucket untouched.");
      machine.water = 129;
      dev.reterra.machine.MachineLogic.stock(machine);
      TestChecks.isTrue(test, 
          machine.water == 129 && machine.getItem(2).is(Items.WATER_BUCKET),
          "A bucket must wait until there is room for all 128 units.");
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void chamberFoodSlotsFollowTheSelectedSpecies(GameTestHelper test) {
    var player = test.makeMockServerPlayerInLevel();
    var chamber =
        new RestorationState.Machine(
            test.absolutePos(new BlockPos(2, 12, 2)), "revival_chamber");
    var handler =
        new dev.reterra.screen.MachineScreenHandler(
            1, player.getInventory(), test.getLevel(), chamber, false);
    handler.clickMenuButton(player, 10);
    for (var sample :
        java.util.List.of(
            Content.COW_SAMPLE, Content.SHEEP_SAMPLE, Content.CHICKEN_SAMPLE, Content.BEE_SAMPLE)) {
      chamber.setItem(3, new ItemStack(sample));
      var expected = dev.reterra.machine.MachineLogic.food(chamber.getItem(3));
      TestChecks.isTrue(test, 
          handler.getSlot(4).mayPlace(new ItemStack(expected)),
          "Food slot must accept the selected animal's food.");
      var wrong = expected == Items.WHEAT ? Items.WHEAT_SEEDS : Items.WHEAT;
      TestChecks.isFalse(test, 
          handler.getSlot(4).mayPlace(new ItemStack(wrong)),
          "Food slot must reject another species' food.");
    }
    chamber.setItem(3, ItemStack.EMPTY);
    TestChecks.isFalse(test, 
        handler.getSlot(4).mayPlace(new ItemStack(Items.WHEAT)),
        "Choose a sample before choosing food.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void stackedSoilCraftingConsumesFourBlocksAndSupportsShiftClick(GameTestHelper test) {
    var player = test.makeMockServerPlayerInLevel();
    var handler = player.inventoryMenu;
    var input = handler.getCraftSlots();
    for (var feedstock : java.util.List.of(Items.COARSE_DIRT, Items.GRAVEL)) {
      player.getInventory().clearContent();
      input.clearContent();
      input.setItem(0, new ItemStack(feedstock, 3));
      input.setItem(1, new ItemStack(Content.COMPOST, 3));
      handler.slotsChanged(input);
      TestChecks.isTrue(test, 
          handler.getSlot(0).getItem().isEmpty(), "Three feedstock must not produce four dirt.");
      input.setItem(0, new ItemStack(feedstock, 12));
      handler.slotsChanged(input);
      var output = handler.getSlot(0).remove(4);
      handler.getSlot(0).onTake(player, output);
      TestChecks.isTrue(test, 
          output.is(Items.DIRT)
              && output.getCount() == 4
              && input.getItem(0).getCount() == 8
              && input.getItem(1).getCount() == 2,
          "Taking four dirt must consume four feedstock and one compost.");
      handler.clicked(0, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
      int dirt = 0;
      for (var stack : java.util.stream.IntStream.range(0, 36).mapToObj(player.getInventory()::getItem).toList())
        if (stack.is(Items.DIRT)) dirt += stack.getCount();
      TestChecks.isTrue(test, 
          dirt == 8 && input.isEmpty(),
          "Shift-click must consume the remaining two batches without duplication.");
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void stackedSoilAlsoWorksInATableAndAutomatedCrafter(GameTestHelper test) {
    var world = test.getLevel();
    var player = test.makeMockServerPlayerInLevel();
    var table =
        new net.minecraft.world.inventory.CraftingMenu(
            1,
            player.getInventory(),
            net.minecraft.world.inventory.ContainerLevelAccess.create(
                world, test.absolutePos(BlockPos.ZERO)));
    table.getSlot(1).setByPlayer(new ItemStack(Items.GRAVEL, 4));
    table.getSlot(9).setByPlayer(new ItemStack(Content.COMPOST));
    var output = table.getSlot(0).remove(4);
    table.getSlot(0).onTake(player, output);
    TestChecks.isTrue(test, 
        output.is(Items.DIRT)
            && output.getCount() == 4
            && table.getSlot(1).getItem().isEmpty()
            && table.getSlot(9).getItem().isEmpty(),
        "The compact recipe must also work across distant crafting-table slots.");
    var pos = test.absolutePos(new BlockPos(2, 14, 2));
    world.setBlockAndUpdate(pos, Blocks.CRAFTER.defaultBlockState());
    var crafter = (net.minecraft.world.level.block.entity.CrafterBlockEntity) world.getBlockEntity(pos);
    crafter.setItem(0, new ItemStack(Items.COARSE_DIRT, 3));
    crafter.setItem(1, new ItemStack(Content.COMPOST, 2));
    TestChecks.isTrue(test, 
        net.minecraft.world.level.block.CrafterBlock.getPotentialResults(world, crafter.asCraftInput())
            .isEmpty(),
        "A crafter must not cache a valid output for insufficient feedstock.");
    crafter.setItem(0, new ItemStack(Items.COARSE_DIRT, 8));
    TestChecks.isTrue(test, 
        net.minecraft.world.level.block.CrafterBlock.getPotentialResults(world, crafter.asCraftInput())
            .isPresent(),
        "Adding the fourth block must invalidate the crafter's insufficient-input result.");
    world
        .getBlockState(pos)
        .tick(world, pos, net.minecraft.util.RandomSource.create(1));
    TestChecks.isTrue(test, 
        crafter.getItem(0).getCount() == 4 && crafter.getItem(1).getCount() == 1,
        "An automated craft must consume four feedstock, not just one.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void wateredCropsGrowFasterOnlyInBarrenWorlds(GameTestHelper test) {
    var world = test.getLevel();
    boolean original = dev.reterra.world.WorldOptions.barren(world);
    var pos = test.absolutePos(new BlockPos(2, 70, 2));
    var crop = (net.minecraft.world.level.block.CropBlock) Blocks.WHEAT;
    try {
      for (boolean barren : new boolean[] {true, false}) {
        dev.reterra.world.WorldOptions.setBarren(world, barren);
        world.setBlockAndUpdate(
            pos.below(),
            Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7));
        world.setBlockAndUpdate(pos, crop.getStateForAge(0));
        var random = net.minecraft.util.RandomSource.create(42);
        for (int i = 0; i < 200 && crop.getAge(world.getBlockState(pos)) == 0; i++)
          world.getBlockState(pos).randomTick(world, pos, random);
        TestChecks.isTrue(test, 
            crop.getAge(world.getBlockState(pos)) == (barren ? 4 : 1),
            "Watered crops should gain four stages in ReTerra and one in ordinary worlds.");
      }
    } finally {
      dev.reterra.world.WorldOptions.setBarren(world, original);
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void farmhousesProvideFoodAndGuaranteedPlantableRoots(GameTestHelper test) {
    var world = test.getLevel();
    var pos = test.absolutePos(new BlockPos(2, 12, 2));
    world.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
    var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) world.getBlockEntity(pos);
    chest.setLootTable(
        net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.LOOT_TABLE, Content.id("chests/farmhouse")),
        123);
    chest.unpackLootTable(null);
    for (var item : java.util.List.of(Items.BREAD, Items.CARROT, Items.POTATO)) {
      int count = 0;
      for (int i = 0; i < chest.getContainerSize(); i++)
        if (chest.getItem(i).is(item)) count += chest.getItem(i).getCount();
      TestChecks.isTrue(test, 
          count == 4, "Every farmhouse must have four " + item.getName(new ItemStack(item)).getString() + ".");
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void coreWorkResumesWithoutLeavingAlternatingRows(GameTestHelper test) {
    var world = test.getLevel();
    var state = RestorationState.get(world);
    int cx = (test.absolutePos(BlockPos.ZERO).getX() >> 4) + 740;
    int cz = (test.absolutePos(BlockPos.ZERO).getZ() >> 4) + 740;
    int left = (cx - 1) * 16, top = (cz - 1) * 16;
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++) {
        world.setChunkForced(cx + x, cz + z, true);
        world.getChunk(cx + x, cz + z);
      }
    try {
      for (int x = 16; x < 32; x++)
        for (int z = 16; z < 32; z++)
          world.setBlockAndUpdate(
              new BlockPos(left + x, 240, top + z), Blocks.COARSE_DIRT.defaultBlockState());
      var core =
          new RestorationState.Machine(
              new BlockPos(cx * 16 + 8, 241, cz * 16 + 8), "restoration_core");
      core.registered = true;
      core.compost = core.fuel = core.water = 1000;
      for (int batch = 0; batch < 24; batch++) {
        int written = dev.reterra.machine.RestorationEngine.restoreTerrain(world, state, core);
        TestChecks.isTrue(test, written <= 6, "Each terrain batch must retain its server work limit.");
      }
      for (int index = 0; index < 96; index++) {
        BlockPos column = dev.reterra.world.RecoveryPattern.column(core.pos, 1, index);
        if (!Ecology.isScar(column.getX(), column.getZ()))
          TestChecks.isTrue(test, 
              world.getBlockState(column.atY(240)).is(Blocks.DIRT),
              "A full batch must resume at its next nearby column without leaving skipped"
                  + " stripes.");
      }
    } finally {
      for (int x = -1; x <= 1; x++)
        for (int z = -1; z <= 1; z++) world.setChunkForced(cx + x, cz + z, false);
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void farmhouseGuidanceUsesACompassTargetInsteadOfTextCoordinates(GameTestHelper test) {
    var world = test.getLevel();
    var state = RestorationState.get(world);
    var before = state.firstSite;
    var completed = new java.util.HashSet<>(state.completed);
    var player = test.makeMockServerPlayerInLevel();
    var discoveries = state.discoveries.remove(player.getUUID());
    try {
      state.completed.clear();
      state.firstSite = test.absolutePos(new BlockPos(20, 4, 20));
      var payload = Journal.payload(player);
      TestChecks.isTrue(test, 
          state.firstSite.equals(payload.farmhouse())
              && payload.objective().equals("Follow the farmhouse compass."),
          "The early objective must carry a compass target without distance or cardinal"
              + " directions.");
      Journal.discover(player, "seeds");
      TestChecks.isTrue(test, 
          Journal.payload(player).farmhouse() == null,
          "The farmhouse compass must disappear after finding seeds.");
    } finally {
      state.firstSite = before;
      state.completed.addAll(completed);
      if (discoveries == null) state.discoveries.remove(player.getUUID());
      else state.discoveries.put(player.getUUID(), discoveries);
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 60)
  public void coresStopSpendingWhenTheirCoverageTargetsAreMet(GameTestHelper test) {
    var w = test.getLevel();
    var state = RestorationState.get(w);
    int cx = (test.absolutePos(BlockPos.ZERO).getX() >> 4) + 300,
        cz = (test.absolutePos(BlockPos.ZERO).getZ() >> 4) + 300;
    w.setChunkForced(cx, cz, true);
    w.getChunk(cx, cz);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        w.setBlockAndUpdate(
            new BlockPos(cx * 16 + x, 180, cz * 16 + z), Blocks.COARSE_DIRT.defaultBlockState());
    var p = new BlockPos(cx * 16 + 8, 181, cz * 16 + 8);
    w.setBlockAndUpdate(p, Content.CORE.defaultBlockState());
    for (var part : Multiblocks.parts("restoration_core"))
      w.setBlockAndUpdate(p.offset(part.x(), part.y(), part.z()), part.block().defaultBlockState());
    var core = new RestorationState.Machine(p, "restoration_core");
    core.registered = true;
    core.sectorCursor = 4;
    core.compost = 32;
    core.fuel = 99;
    core.water = 99;
    core.culture = 88;
    for (var s : core.sectors) {
      s.surveyed = true;
      s.lastSampled = 256;
      s.lastFertile = 200;
      s.lastGrass = 128;
    }
    state.machines.put(p.asLong(), core);
    test.runAtTickTime(
        25,
        () -> {
          TestChecks.isTrue(test, 
              core.compost == 32
                  && core.fuel == 99
                  && core.water == 99
                  && core.culture == 0
                  && core.getItem(3).is(Content.CULTURE),
              "A Core waiting for plants and wildlife must stop terrain spending once its coverage"
                  + " goals are met.");
          state.machines.remove(p.asLong());
          w.setChunkForced(cx, cz, false);
          test.succeed();
        });
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void archivesRecoverDifferentSpeciesAndDoNotRerollOpenedChests(GameTestHelper test) {
    var w = test.getLevel();
    var state = RestorationState.get(w);
    var before = new java.util.HashSet<>(state.recoveredSamples);
    state.recoveredSamples.clear();
    java.util.Set<net.minecraft.world.item.Item> found = new java.util.HashSet<>();
    var table =
        net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.LOOT_TABLE,
            Content.id("chests/biological_archive"));
    for (int i = 0; i < 4; i++) {
      var p = test.absolutePos(new BlockPos(2 + i, 12, 2));
      w.setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState());
      var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) w.getBlockEntity(p);
      chest.setLootTable(table, 12345);
      chest.unpackLootTable(null);
      int count = 0;
      for (int slot = 0; slot < chest.getContainerSize(); slot++)
        if (dev.reterra.machine.MachineLogic.sample(chest.getItem(slot))) {
          count += chest.getItem(slot).getCount();
          found.add(chest.getItem(slot).getItem());
        }
      TestChecks.isTrue(test, count == 1, "Each archive must still preserve only one animal sample.");
      chest.unpackLootTable(null);
      TestChecks.isTrue(test, 
          state.recoveredSamples.size() == i + 1,
          "Opening a chest again must not sequence another specimen.");
    }
    TestChecks.isTrue(test, 
        found.size() == 4,
        "Four identical raw loot rolls must recover all four species before repeating.");
    state.recoveredSamples.clear();
    state.recoveredSamples.addAll(before);
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void scannerPreviewsAreReadOnlyAndNeverRegisterARegion(GameTestHelper test) {
    var w = test.getLevel();
    var state = RestorationState.get(w);
    var p = test.absolutePos(new BlockPos(3, 12, 3));
    var player = test.makeMockServerPlayerInLevel();
    player.snapTo(p.getX(), p.getY(), p.getZ(), 0, 0);
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Content.SCANNER));
    int records = state.machines.size();
    var handler =
        dev.reterra.screen.MachineScreenHandler.previewHandler(1, player.getInventory(), w, p);
    TestChecks.isTrue(test, 
        handler.stillValid(player)
            && handler.value(45) == 1
            && handler.readOnly()
            && handler.value(18) > 0,
        "A scanner must show an actual local survey before a machine exists.");
    TestChecks.isFalse(test, 
        handler.clickMenuButton(player, 1), "Preview cannot register or operate a machine.");
    TestChecks.isFalse(test, handler.clickMenuButton(player, 10), "Preview has no machine controls tab.");
    TestChecks.isTrue(test, 
        handler.clickMenuButton(player, 4) && state.previewBorders.containsKey(player.getUUID()),
        "Preview can display a temporary boundary.");
    TestChecks.isTrue(test, 
        state.machines.size() == records && player.getMainHandItem().is(Content.SCANNER),
        "Surveying must not consume the scanner or create persistent machines.");
    state.previewBorders.remove(player.getUUID());
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void firstRegistrationHasAnIntroductoryContractAndCarePersists(GameTestHelper test) {
    var w = test.getLevel();
    var state = RestorationState.get(w);
    var machines = new java.util.LinkedHashMap<>(state.machines);
    var regions = new java.util.LinkedHashMap<>(state.reclaimedRegions);
    var completed = new java.util.HashSet<>(state.completed);
    state.machines.clear();
    state.reclaimedRegions.clear();
    state.completed.clear();
    var p = test.absolutePos(BlockPos.ZERO);
    var first = new RestorationState.Machine(p, "restoration_core");
    var second = new RestorationState.Machine(p.east(64), "restoration_core");
    state.machines.put(p.asLong(), first);
    state.machines.put(second.pos.asLong(), second);
    TestChecks.isTrue(test, 
        dev.reterra.machine.MachineLogic.register(w, first) && first.introductory,
        "The first project must have an easier contract.");
    TestChecks.isTrue(test, 
        dev.reterra.machine.MachineLogic.register(w, second) && !second.introductory,
        "A simultaneous second project must retain full ecosystem goals.");
    first.careTasks.add("feed");
    var id = java.util.UUID.randomUUID();
    first.caretakers.add(id);
    var copy =
        RestorationState.fromNbt(
            state.writeNbt(new CompoundTag(), w.registryAccess()), w.registryAccess());
    var saved = copy.machines.get(p.asLong());
    TestChecks.isTrue(test, 
        saved.introductory && saved.careTasks.contains("feed") && saved.caretakers.contains(id),
        "Sanctuary and stewardship progress must survive restart.");
    state.machines.clear();
    state.machines.putAll(machines);
    state.reclaimedRegions.clear();
    state.reclaimedRegions.putAll(regions);
    state.completed.clear();
    state.completed.addAll(completed);
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 80)
  public void realHabitatCareRewardsThreeDistinctActionsOnly(GameTestHelper test) {
    var w = test.getLevel();
    var state = RestorationState.get(w);
    int cx = (test.absolutePos(BlockPos.ZERO).getX() >> 4) + 280,
        cz = (test.absolutePos(BlockPos.ZERO).getZ() >> 4) + 280;
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) {
        w.setChunkForced(cx + dx, cz + dz, true);
        w.getChunk(cx + dx, cz + dz);
      }
    var p = new BlockPos(cx * 16 + 8, 181, cz * 16 + 8);
    w.setBlockAndUpdate(p, Content.CORE.defaultBlockState());
    for (var part : Multiblocks.parts("restoration_core"))
      w.setBlockAndUpdate(p.offset(part.x(), part.y(), part.z()), part.block().defaultBlockState());
    var core = new RestorationState.Machine(p, "restoration_core");
    core.registered = true;
    core.paused = true;
    for (var sector : core.sectors) {
      sector.surveyed = true;
      sector.lastSampled = 256;
      sector.lastFertile = 200;
      sector.lastGrass = 120;
      sector.lastWater = 8;
      sector.lastCrops = 12;
      sector.lastTrees = 48;
      sector.lastFlowers = 5;
      sector.lastSpecies.addAll(
          java.util.List.of(
              "minecraft:wheat",
              "minecraft:dandelion",
              "minecraft:poppy",
              "minecraft:oak_leaves",
              "minecraft:birch_leaves"));
    }
    state.machines.put(p.asLong(), core);
    test.runAtTickTime(
        20,
        () -> {
          var cow = TestAccess.create(dev.reterra.compat.GameAccess.COW, w);
          var sheep = TestAccess.create(dev.reterra.compat.GameAccess.SHEEP, w);
          cow.snapTo(p.getX() + 5, p.getY(), p.getZ() + 5, 0, 0);
          sheep.snapTo(p.getX() - 5, p.getY(), p.getZ() - 5, 0, 0);
          w.addFreshEntity(cow);
          w.addFreshEntity(sheep);
          core.paused = false;
          core.stableSeconds = 10;
          var player = test.makeMockServerPlayerInLevel();
          player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
          player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 4));
          cow.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
          TestChecks.isTrue(test, 
              core.stableSeconds == 25 && cow.isInLove(),
              "Actually feeding wildlife during a healthy trial should earn 15 seconds: stable="
                  + core.stableSeconds + ", love=" + cow.isInLove()
                  + ", species=" + dev.reterra.machine.RestorationEngine.animalSpecies(w, core)
                  + ", loaded=" + dev.reterra.machine.RestorationEngine.allSectorsLoaded(w, core)
                  + ", assembly=" + Multiblocks.issue(w, core.pos, core.kind));
          var grass = p.east(6).below();
          w.setBlockAndUpdate(grass, Blocks.GRASS_BLOCK.defaultBlockState());
          w.setBlockAndUpdate(grass.above(), Blocks.AIR.defaultBlockState());
          player.setItemInHand(
              net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Content.COMPOST, 2));
          var hit =
              new net.minecraft.world.phys.BlockHitResult(
                  net.minecraft.world.phys.Vec3.atCenterOf(grass),
                  net.minecraft.core.Direction.UP,
                  grass,
                  false);
          dev.reterra.machine.MachineInteraction.use(
              player, w, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
          var crop = p.west(6);
          w.setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState());
          w.setBlockAndUpdate(crop, Blocks.WHEAT.defaultBlockState());
          var cropHit =
              new net.minecraft.world.phys.BlockHitResult(
                  net.minecraft.world.phys.Vec3.atCenterOf(crop),
                  net.minecraft.core.Direction.UP,
                  crop,
                  false);
          dev.reterra.machine.MachineInteraction.use(
              player, w, net.minecraft.world.InteractionHand.MAIN_HAND, cropHit);
          TestChecks.isTrue(test, 
              core.stableSeconds == 55
                  && core.careTasks.size() == 3
                  && w.getBlockState(crop).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7) == 2,
              "Planting and crop tending must give distinct bonuses while compost visibly grows"
                  + " crops.");
          TestChecks.isFalse(test, 
              dev.reterra.machine.Stewardship.credit(w, crop, "tend"),
              "Repeated tending must not farm trial time.");
          TestChecks.isTrue(test, 
              core.stableSeconds == 55, "Care rewards must cap at 45 seconds per region.");
          cow.discard();
          sheep.discard();
          state.machines.remove(p.asLong());
          for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) w.setChunkForced(cx + dx, cz + dz, false);
          test.succeed();
        });
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void surveyCompassPointsToConfirmedSitesWithoutLosingTheCompass(GameTestHelper test) {
    var player = test.makeMockServerPlayerInLevel();
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Content.FIELD_JOURNAL));
    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.COMPASS));
    var target = test.absolutePos(new BlockPos(3, 12, 3));
    TestChecks.isTrue(test, 
        dev.reterra.world.SurveyClues.point(
            player, dev.reterra.world.ResearchSites.Kind.GREENHOUSE, target),
        "A traced site should update the held compass.");
    var compass = player.getOffhandItem();
    var tracker = compass.get(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER);
    TestChecks.isTrue(test, 
        compass.is(Items.COMPASS)
            && tracker.target().orElseThrow().pos().equals(target)
            && !tracker.tracked(),
        "The compass should navigate to the ruin without requiring a surviving lodestone.");
    var tag = new CompoundTag();
    tag.putString("reterra_clue", "seed_vault");
    player
        .getMainHandItem()
        .set(
            net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.of(tag));
    TestChecks.isTrue(test, 
        dev.reterra.world.SurveyClues.destination(player.getMainHandItem(), 0)
            == dev.reterra.world.ResearchSites.Kind.SEED_VAULT,
        "Historical notes must retain their own destination rather than always following the"
            + " current chapter.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void firstFarmhouseIsRecordedAndNeverOverwritesPlayerBuilds(GameTestHelper test) {
    var w = test.getLevel();
    var state = RestorationState.get(w);
    BlockPos previous = state.firstSite;
    boolean pending = state.firstSitePending;
    int cx = (test.absolutePos(BlockPos.ZERO).getX() >> 4) + 240;
    int cz = (test.absolutePos(BlockPos.ZERO).getZ() >> 4) + 240;
    w.setChunkForced(cx, cz, true);
    w.getChunk(cx, cz);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        w.setBlockAndUpdate(
            new BlockPos(cx * 16 + x, 180, cz * 16 + z), Blocks.COARSE_DIRT.defaultBlockState());
        for (int y = 181; y < 192; y++)
          w.setBlockAndUpdate(new BlockPos(cx * 16 + x, y, cz * 16 + z), Blocks.AIR.defaultBlockState());
      }
    BlockPos site = new BlockPos(cx * 16 + 8, 181, cz * 16 + 8);
    BlockPos spawn = site.offset(32, 0, 32);
    state.protectedBlocks.add(site.asLong());
    dev.reterra.world.ResearchSites.ensureFirstSite(w, spawn);
    TestChecks.isTrue(test, 
        w.getBlockState(site).isAir(), "A player-protected first-site candidate must be skipped.");
    state.protectedBlocks.remove(site.asLong());
    TestChecks.isTrue(test, 
        dev.reterra.world.ResearchSites.ensureFirstSite(w, spawn),
        "A suitable loaded farmhouse site should be placed even after the initial join window.");
    TestChecks.isTrue(test, 
        state.firstSite.equals(site)
            && !state.firstSitePending
            && w.getBlockEntity(
                    dev.reterra.world.ResearchSites.chestPosition(
                        site, dev.reterra.world.ResearchSites.Kind.FARMHOUSE))
                != null,
        "A physical starter ruin and its position must be recorded for retries and directions.");
    state.knownSites.remove(site.asLong());
    state.firstSite = previous;
    state.firstSitePending = pending;
    w.setChunkForced(cx, cz, false);
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void riverRecoveryRefillsOnlyExposedUnprotectedRiverbeds(GameTestHelper test) {
    var w = test.getLevel();
    int cx = (test.absolutePos(BlockPos.ZERO).getX() >> 4) + 220;
    int cz = (test.absolutePos(BlockPos.ZERO).getZ() >> 4) + 220;
    w.setChunkForced(cx, cz, true);
    w.getChunk(cx, cz);
    BlockPos bed = new BlockPos(cx * 16 + 4, w.getSeaLevel() - 5, cz * 16 + 4);
    var river =
        w.registryAccess()
            .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
            .get(net.minecraft.resources.Identifier.withDefaultNamespace("river"))
            .orElseThrow();
    net.minecraft.server.commands.FillBiomeCommand.fill(
        w, new BlockPos(cx * 16, 48, cz * 16), new BlockPos(cx * 16 + 15, 80, cz * 16 + 15), river);
    for (int dy = 1; dy <= 40; dy++) w.setBlockAndUpdate(bed.above(dy), Blocks.AIR.defaultBlockState());
    w.setBlockAndUpdate(bed.below(), Blocks.STONE.defaultBlockState());
    w.setBlockAndUpdate(bed, Blocks.GRAVEL.defaultBlockState());
    var state = RestorationState.get(w);
    state.protectedBlocks.add(bed.above(2).asLong());
    TestChecks.isFalse(test, 
        dev.reterra.world.RiverRecovery.restoreColumn(w, bed.getX(), bed.getZ()),
        "A protected channel column must never be flooded.");
    state.protectedBlocks.remove(bed.above(2).asLong());
    var pump = new RestorationState.Machine(bed.above(5), "water_restorer");
    pump.water = 10;
    pump.fuel = 10;
    pump.compost = 8;
    pump.riverCursor = 312;
    TestChecks.isTrue(test, 
        dev.reterra.world.RiverRecovery.tick(w, pump) == 1
            && pump.water == 9
            && pump.fuel == 9
            && pump.compost == 7
            && pump.riverRestored == 1,
        "Landscape pumping must charge supplies only for sources actually restored.");
    for (int i = 1; i < 4; i++)
      TestChecks.isTrue(test, 
          dev.reterra.world.RiverRecovery.restoreColumn(w, bed.getX(), bed.getZ()),
          "River sources must rise gradually from the bed.");
    TestChecks.isFalse(test, 
        dev.reterra.world.RiverRecovery.restoreColumn(w, bed.getX(), bed.getZ()),
        "Recovered water must never rise above the old sea level.");
    TestChecks.isTrue(test, 
        w.getBlockState(bed.above(4)).is(Blocks.WATER) && w.getBlockState(bed.above(5)).isAir(),
        "Historic waterline must be respected.");
    BlockPos cave = bed.east(4);
    w.setBlockAndUpdate(cave, Blocks.GRAVEL.defaultBlockState());
    w.setBlockAndUpdate(cave.below(), Blocks.AIR.defaultBlockState());
    TestChecks.isFalse(test, 
        dev.reterra.world.RiverRecovery.restoreColumn(w, cave.getX(), cave.getZ()),
        "An unsupported cave roof is not a riverbed.");
    var plains =
        w.registryAccess()
            .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
            .get(net.minecraft.resources.Identifier.withDefaultNamespace("plains"))
            .orElseThrow();
    net.minecraft.server.commands.FillBiomeCommand.fill(
        w,
        new BlockPos(cx * 16, 48, cz * 16),
        new BlockPos(cx * 16 + 15, 80, cz * 16 + 15),
        plains);
    w.setBlockAndUpdate(bed.above(4), Blocks.AIR.defaultBlockState());
    TestChecks.isFalse(test, 
        dev.reterra.world.RiverRecovery.restoreColumn(w, bed.getX(), bed.getZ()),
        "Low plains must not be turned into rivers.");
    w.setChunkForced(cx, cz, false);
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void localSurveyCountsWatersideVegetationAndFertileSlopes(GameTestHelper test) {
    var w = test.getLevel();
    BlockPos p = test.absolutePos(new BlockPos(3, 12, 3));
    w.setBlockAndUpdate(p, Blocks.GRASS_BLOCK.defaultBlockState());
    w.setBlockAndUpdate(p.above(), Blocks.SUGAR_CANE.defaultBlockState());
    w.setBlockAndUpdate(p.east(), Blocks.WATER.defaultBlockState());
    w.setBlockAndUpdate(p.west(), Blocks.AIR.defaultBlockState());
    w.setBlockAndUpdate(p.west().below(), Blocks.STONE.defaultBlockState());
    w.setBlockAndUpdate(p.above(3), Blocks.OAK_LEAVES.defaultBlockState());
    var sector = new RestorationState.Sector();
    dev.reterra.machine.RestorationEngine.sample(w, sector, p);
    TestChecks.isTrue(test, 
        sector.wetland == 1 && sector.slopes == 1 && sector.species.size() == 2,
        "Cane beside water, fertile terraces and native leaf types must be measured from real"
            + " blocks.");
    sector.finish();
    var core = new RestorationState.Machine(p, "restoration_core");
    core.sectors[0] = sector;
    var s = core.survey(0);
    TestChecks.isTrue(test, 
        s.wetland() == 1 && s.slopes() == 1 && s.treeTypes() == 1,
        "Finished surveys must retain the new ecosystem measurements.");
    w.setBlockAndUpdate(p.above(), Blocks.OAK_LOG.defaultBlockState());
    var grown = new RestorationState.Sector();
    dev.reterra.machine.RestorationEngine.sample(w, grown, p);
    TestChecks.isTrue(test, 
        grown.wetland == 1, "Growing a waterside tree must not remove its wetland contribution.");
    var local = dev.reterra.machine.RestorationEngine.localSurvey(w, p);
    TestChecks.isTrue(test, 
        local.sampled() > 0 && local.species() > 0,
        "A scanner survey must work without registering any Core.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void worldOptionDisablesStrippingAndNaturalWildlifeSuppression(GameTestHelper test) {
    var w = test.getLevel();
    boolean before = dev.reterra.world.WorldOptions.barren(w);
    dev.reterra.world.WorldOptions.setBarren(w, false);
    try {
      var p = test.absolutePos(new BlockPos(3, 12, 3));
      w.setBlockAndUpdate(p, Blocks.GRASS_BLOCK.defaultBlockState());
      dev.reterra.world.BarrenWorld.transform(w, w.getChunk(p));
      TestChecks.isTrue(test, 
          w.getBlockState(p).is(Blocks.GRASS_BLOCK),
          "Disabling barren generation must preserve new terrain too.");
      var fish = TestAccess.create(dev.reterra.compat.GameAccess.COD, w);
      fish.finalizeSpawn(w, w.getCurrentDifficultyAt(p), net.minecraft.world.entity.EntitySpawnReason.NATURAL, null);
      TestChecks.isFalse(test, 
          fish.entityTags().contains("reterra_barren_spawn"),
          "Ordinary worlds must retain natural wildlife.");
      dev.reterra.world.WorldOptions.setBarren(w, true);
      var barrenFish = TestAccess.create(dev.reterra.compat.GameAccess.COD, w);
      barrenFish.finalizeSpawn(
          w, w.getCurrentDifficultyAt(p), net.minecraft.world.entity.EntitySpawnReason.NATURAL, null);
      TestChecks.isTrue(test, 
          barrenFish.entityTags().contains("reterra_barren_spawn"),
          "Fish must also be suppressed in a dead world.");
    } finally {
      dev.reterra.world.WorldOptions.setBarren(w, before);
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void discoveriesRiverModeAndFinaleSurviveSaving(GameTestHelper test) {
    var state = new RestorationState();
    var p = test.absolutePos(BlockPos.ZERO);
    state.firstSite = p;
    state.firstSitePending = true;
    state.heartActivated = true;
    state.heartPos = p.above();
    state.heartCursor = 123;
    state.heartTicks = 140;
    var id = java.util.UUID.randomUUID();
    state.discoveries.put(id, new java.util.HashSet<>(java.util.Set.of("seeds", "wheat")));
    var pump = new RestorationState.Machine(p, "water_restorer");
    pump.riverMode = true;
    pump.riverCursor = 72;
    pump.riverRestored = 30;
    pump.celebratedStage = 2;
    pump.sectors[0].lastWetland = 8;
    pump.sectors[0].lastSlopes = 12;
    state.machines.put(p.asLong(), pump);
    var copy =
        RestorationState.fromNbt(
            state.writeNbt(new CompoundTag(), test.getLevel().registryAccess()),
            test.getLevel().registryAccess());
    var machine = copy.machines.get(p.asLong());
    TestChecks.isTrue(test, 
        copy.heartPos.equals(p.above())
            && copy.firstSite.equals(p)
            && copy.firstSitePending
            && copy.heartCursor == 123
            && copy.heartTicks == 140
            && copy.discoveries.get(id).contains("wheat"),
        "Journey and finale progress must persist.");
    TestChecks.isTrue(test, 
        machine.riverMode
            && machine.riverRestored == 30
            && machine.riverCursor == 72
            && machine.celebratedStage == 2
            && machine.survey(0).wetland() == 8
            && machine.survey(0).slopes() == 12,
        "River, milestone and biome-specific surveys must persist.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void firstEcosystemImprovesLaterRestorationSupplies(GameTestHelper test) {
    var state = RestorationState.get(test.getLevel());
    var before = java.util.EnumSet.noneOf(Ecology.Family.class);
    before.addAll(state.completed);
    state.completed.add(Ecology.Family.PLAINS);
    var m = new RestorationState.Machine(test.absolutePos(BlockPos.ZERO), "restoration_core");
    m.setItem(0, new ItemStack(Content.COMPOST));
    m.setItem(1, new ItemStack(Items.COAL));
    m.setItem(3, new ItemStack(Content.CULTURE));
    dev.reterra.machine.MachineLogic.stock(test.getLevel(), m);
    TestChecks.isTrue(test, 
        m.compost == 48 && m.fuel == 160 && m.culture == 0 && m.getItem(3).is(Content.CULTURE),
        "Research improves soil and fuel yields; grass cultures remain available for hand"
            + " planting.");
    state.completed.clear();
    state.completed.addAll(before);
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void awakenedHeartLightsUpAndHealsBeyondARegion(GameTestHelper test) {
    var w = test.getLevel();
    var state = RestorationState.get(w);
    var completed = java.util.EnumSet.noneOf(Ecology.Family.class);
    completed.addAll(state.completed);
    boolean active = state.heartActivated;
    BlockPos previousHeart = state.heartPos;
    int previousCursor = state.heartCursor, previousTicks = state.heartTicks;
    BlockPos p = test.absolutePos(new BlockPos(3, 12, 3));
    w.setBlockAndUpdate(p, Content.HEART.defaultBlockState());
    for (var part : Multiblocks.parts("heart_of_the_world"))
      w.setBlockAndUpdate(p.offset(part.x(), part.y(), part.z()), part.block().defaultBlockState());
    var m = new RestorationState.Machine(p, "heart_of_the_world");
    int previousAwakening = state.heartAwakening;
    state.heartAwakening = 0;
    var previousCrystal = state.heartCrystal;
    state.machines.put(p.asLong(), m);
    state.completed.addAll(java.util.EnumSet.allOf(Ecology.Family.class));
    state.heartActivated = false;
    var player = test.makeMockServerPlayerInLevel();
    dev.reterra.machine.MachineLogic.action(w, m, player, 1);
    TestChecks.isTrue(test, 
        !state.heartActivated && state.heartAwakening == 1,
        "The Heart must finish its animation before activation.");
    for (int i = 0; i < 64; i++) dev.reterra.world.HeartAwakening.tick(w, state);
    TestChecks.isTrue(test, 
        state.heartActivated
            && p.equals(state.heartPos)
            && w.getBlockState(p).getValue(dev.reterra.machine.MachineBlock.LIT),
        "Awakening must permanently light the Heart.");
    BlockPos ground = p.east(5).below();
    w.setBlockAndUpdate(ground, Blocks.COARSE_DIRT.defaultBlockState());
    w.setBlockAndUpdate(ground.above(), Blocks.AIR.defaultBlockState());
    state.heartCursor = recoveryCursor(p, 8, ground);
    dev.reterra.world.WorldRecovery.tick(w, state);
    TestChecks.isTrue(test, 
        w.getBlockState(ground).is(Blocks.DIRT)
            || w.getBlockState(ground).is(Blocks.GRASS_BLOCK),
        "The finale must perform actual passive terrain recovery.");
    state.heartTicks = 100;
    dev.reterra.machine.MachineLogic.action(w, m, player, 1);
    TestChecks.isTrue(test, 
        state.heartTicks == 100
            && dev.reterra.machine.MachineLogic.status(w, m)
                == dev.reterra.machine.MachineLogic.Status.COMPLETE,
        "An awakened Heart must not reactivate or restart the finale.");
    state.completed.clear();
    state.completed.addAll(completed);
    state.heartActivated = active;
    state.heartPos = previousHeart;
    state.heartCursor = previousCursor;
    state.heartTicks = previousTicks;
    state.heartAwakening = previousAwakening;
    state.heartCrystal = previousCrystal;
    state.machines.remove(p.asLong());
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void guideTracksDiscoveriesInsteadOfRestartingAtSeeds(GameTestHelper test) {
    var player = test.makeMockServerPlayerInLevel();
    Journal.discover(player, "seeds");
    Journal.discover(player, "compost");
    TestChecks.isTrue(test, 
        Journal.payload(player).chapter() == 1
            && Journal.payload(player).checklist().contains("[x] Make organic compost"),
        "First-growth notes must show completed discoveries.");
    Journal.discover(player, "dirt");
    Journal.discover(player, "wheat");
    TestChecks.isTrue(test, 
        Journal.payload(player).chapter() == 2,
        "A working farm must advance the guide toward machinery.");
    Journal.discover(player, "habitat_ready");
    TestChecks.isTrue(test, 
        Journal.payload(player).chapter() == 4,
        "A habitat milestone must survive leaving its Core region.");
    Journal.discover(player, "natures_return");
    TestChecks.isTrue(test, 
        Journal.payload(player).chapter() == 4,
        "One revived pair must not skip stabilization and reclamation.");
    Journal.discover(player, "reclaimed");
    TestChecks.isTrue(test, 
        Journal.payload(player).chapter() == 5, "Reclamation starts the final chapter.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void upgradedReservesAndIncubationProgressPersist(GameTestHelper test) {
    var state = new RestorationState();
    var legacy =
        new RestorationState.Machine(test.absolutePos(BlockPos.ZERO), "restoration_core");
    legacy.compost = 256;
    legacy.fuel = 2048;
    legacy.water = 2048;
    legacy.culture = 1024;
    state.machines.put(legacy.pos.asLong(), legacy);
    var chamber = new RestorationState.Machine(legacy.pos.east(), "revival_chamber");
    chamber.pendingSpecies = "bee_sample";
    chamber.revivalTicks = 175;
    chamber.water = 16;
    chamber.setItem(3, new ItemStack(Content.BEE_SAMPLE));
    chamber.setItem(4, new ItemStack(Items.POPPY, 2));
    state.machines.put(chamber.pos.asLong(), chamber);
    var nbt = state.writeNbt(new CompoundTag(), test.getLevel().registryAccess());
    dev.reterra.compat.NbtAccess.compound(dev.reterra.compat.NbtAccess.compounds(nbt, "machines"), 0)
        .putInt("resourceVersion", 2);
    var restored = RestorationState.fromNbt(nbt, test.getLevel().registryAccess());
    var migrated = restored.machines.get(legacy.pos.asLong());
    TestChecks.isTrue(test, 
        migrated.compost == 32
            && migrated.fuel == 128
            && migrated.water == 128
            && migrated.culture == 128,
        "An old dose must convert once to a new dose without losing its item value.");
    var pending = restored.machines.get(chamber.pos.asLong());
    TestChecks.isTrue(test, 
        pending.revivalTicks == 175
            && pending.pendingSpecies.equals("bee_sample")
            && pending.getItem(4).getCount() == 2
            && pending.water == 16,
        "Saving must preserve incubation and its reserved inputs.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 80)
  public void nearbyHomeAnimalsAndShelteredBeesStayInTheCensus(GameTestHelper test) {
    var w = test.getLevel();
    int cx = (test.absolutePos(BlockPos.ZERO).getX() >> 4) + 180;
    int cz = (test.absolutePos(BlockPos.ZERO).getZ() >> 4) + 180;
    w.setChunkForced(cx, cz, true);
    w.setChunkForced(cx + 2, cz, true);
    w.getChunk(cx, cz);
    w.getChunk(cx + 2, cz);
    test.runAtTickTime(
        20,
        () -> {
          var core =
              new RestorationState.Machine(
                  new BlockPos(cx * 16 + 8, 180, cz * 16 + 8), "restoration_core");
          var sheep = TestAccess.create(dev.reterra.compat.GameAccess.SHEEP, w);
          sheep.snapTo(cx * 16 + 34, 180, cz * 16 + 8, 0, 0);
          sheep.addTag(dev.reterra.machine.AnimalHabitatGoal.PREFIX + core.pos.asLong());
          TestChecks.isTrue(test, 
              w.addFreshEntity(sheep), "Home-animal fixture must spawn into its loaded chunk.");
          TestChecks.isTrue(test, 
              dev.reterra.machine.RestorationEngine.animalCount(w, core) == 1,
              "A home animal just beyond the survey edge must remain counted: "
                  + dev.reterra.machine.RestorationEngine.animalCount(w, core)
                  + ", alive="
                  + sheep.isAlive()
                  + ", baby="
                  + sheep.isBaby()
                  + ", tags="
                  + sheep.entityTags());
          TestChecks.isTrue(test, 
              ((dev.reterra.mixin.MobGoalsAccessor) sheep)
                  .reterra$getGoals().getAvailableGoals().stream()
                      .anyMatch(
                          goal -> goal.getGoal() instanceof dev.reterra.machine.AnimalHabitatGoal),
              "Loaded home animals need a return-home goal.");
          w.setBlockAndUpdate(core.pos, Blocks.BEEHIVE.defaultBlockState());
          var hive = (net.minecraft.world.level.block.entity.BeehiveBlockEntity) w.getBlockEntity(core.pos);
          hive.storeBee(net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant.create(600));
          TestChecks.isTrue(test, 
              dev.reterra.machine.RestorationEngine.animalCount(w, core) == 2
                  && dev.reterra.machine.RestorationEngine.animalSpecies(w, core) == 2,
              "Sheltered bees still count as wildlife.");
          sheep.discard();
          w.setBlockAndUpdate(core.pos, Blocks.AIR.defaultBlockState());
          w.setChunkForced(cx, cz, false);
          w.setChunkForced(cx + 2, cz, false);
          test.succeed();
        });
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void everyMachineCanRotateAndTheCoreNeedsItsFurnace(GameTestHelper test) {
    var w = test.getLevel();
    BlockPos p = test.absolutePos(new BlockPos(3, 12, 3));
    for (String kind : dev.reterra.machine.MachineLogic.KINDS) {
      for (int rotation = 0; rotation < 4; rotation++) {
        for (int dx = -3; dx <= 3; dx++)
          for (int dz = -3; dz <= 3; dz++)
            w.setBlockAndUpdate(p.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
        w.setBlockAndUpdate(
            p, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Content.id(kind)).defaultBlockState());
        for (var part : Multiblocks.parts(kind, rotation))
          w.setBlockAndUpdate(p.offset(part.x(), part.y(), part.z()), part.block().defaultBlockState());
        TestChecks.isTrue(test, 
            Multiblocks.issue(w, p, kind) == 0,
            "Valid rotation rejected: " + kind + " " + rotation);
        if (kind.equals("restoration_core")) {
          w.setBlockAndUpdate(p.below(), Blocks.POLISHED_ANDESITE.defaultBlockState());
          TestChecks.isTrue(test, Multiblocks.issue(w, p, kind) != 0, "A Core must require its furnace.");
        }
      }
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void waterBucketsReturnInTheirInputSlotAndReservesDoNotBurnWhileIdle(GameTestHelper test) {
    var m = new RestorationState.Machine(test.absolutePos(BlockPos.ZERO), "restoration_core");
    m.setItem(0, new ItemStack(Content.COMPOST));
    m.setItem(1, new ItemStack(Items.COAL));
    m.setItem(2, new ItemStack(Items.WATER_BUCKET));
    m.setItem(3, new ItemStack(Content.CULTURE));
    dev.reterra.machine.MachineLogic.stock(m);
    TestChecks.isTrue(test, 
        m.getItem(2).is(Items.BUCKET) && m.getItem(7).isEmpty(),
        "Empty bucket must stay in the water slot.");
    int water = m.water, fuel = m.fuel, compost = m.compost;
    for (int i = 0; i < 20; i++) dev.reterra.machine.MachineLogic.stock(m);
    TestChecks.isTrue(test, 
        m.water == water && m.fuel == fuel && m.compost == compost,
        "Waiting must preserve stored supplies.");
    TestChecks.isTrue(test, 
        water < 256 && fuel < 256 && compost < 256, "One item must not restore an entire region.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void pumpSuppliesNearbyMachinesDirectlyFromAPond(GameTestHelper test) {
    var w = test.getLevel();
    var state = RestorationState.get(w);
    BlockPos p = test.absolutePos(new BlockPos(3, 12, 3));
    var pump = new RestorationState.Machine(p, "water_restorer");
    var core = new RestorationState.Machine(p.east(6), "restoration_core");
    pump.fuel = 128;
    pump.compost = 8;
    w.setBlockAndUpdate(p.north(), Blocks.WATER.defaultBlockState());
    state.machines.put(p.asLong(), pump);
    state.machines.put(core.pos.asLong(), core);
    dev.reterra.machine.MachineLogic.pump(w, pump);
    TestChecks.isTrue(test, 
        core.water == 32 && pump.fuel == 127 && pump.compost == 7,
        "Pump must transfer water while charging for actual pumping.");
    TestChecks.isTrue(test, 
        w.getBlockState(p.north()).is(Blocks.WATER) && core.getItem(2).isEmpty(),
        "Pond-fed irrigation needs no bucket hauling.");
    state.machines.remove(p.asLong());
    state.machines.remove(core.pos.asLong());
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void flowersCanBeCultivatedWithoutFindingMoreRuins(GameTestHelper test) {
    var w = test.getLevel();
    BlockPos p = test.absolutePos(new BlockPos(3, 12, 3));
    for (int dx = -2; dx <= 2; dx++)
      for (int dz = -2; dz <= 2; dz++) {
        w.setBlockAndUpdate(p.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState());
        w.setBlockAndUpdate(p.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
      }
    var player = test.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Content.COMPOST));
    var hit =
        new net.minecraft.world.phys.BlockHitResult(
            net.minecraft.world.phys.Vec3.atCenterOf(p),
            net.minecraft.core.Direction.UP,
            p,
            false);
    dev.reterra.machine.MachineInteraction.use(player, w, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
    int flowers = 0;
    for (int dx = -2; dx <= 2; dx++)
      for (int dz = -2; dz <= 2; dz++)
        if (w.getBlockState(p.offset(dx, 1, dz)).is(net.minecraft.tags.BlockTags.FLOWERS))
          flowers++;
    TestChecks.isTrue(test, 
        flowers == 5 && player.getMainHandItem().isEmpty(),
        "One compost should grow enough flowers to keep a habitat and supply two bee food.");
    TestChecks.isTrue(test, 
        dev.reterra.machine.MachineLogic.foodMatches(
                new ItemStack(Content.BEE_SAMPLE), new ItemStack(Items.POPPY))
            && dev.reterra.machine.MachineLogic.foodCount(new ItemStack(Content.BEE_SAMPLE)) == 2,
        "Bees accept two flowers, including poppies, rather than demanding one specific flower.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void machinesShowStoredSuppliesAndActivity(GameTestHelper test) {
    var w = test.getLevel();
    BlockPos p = test.absolutePos(new BlockPos(3, 12, 3));
    assemble(w, p);
    var m = new RestorationState.Machine(p, "soil_reclaimer");
    m.setItem(0, new ItemStack(Content.COMPOST));
    dev.reterra.machine.MachineBlock.updateVisual(w, m);
    TestChecks.isTrue(test, 
        w.getBlockState(p).getValue(dev.reterra.machine.MachineBlock.SUPPLIED)
            && !w.getBlockState(p).getValue(dev.reterra.machine.MachineBlock.LIT),
        "A stocked but idle machine needs its amber indicator.");
    m.compost = 32;
    m.fuel = 128;
    m.water = 128;
    dev.reterra.machine.MachineBlock.updateVisual(w, m);
    TestChecks.isTrue(test, 
        w.getBlockState(p).getValue(dev.reterra.machine.MachineBlock.LIT),
        "A working machine needs its lit indicator.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void reclaimedRecoverySpreadsPastItsOriginalBorder(GameTestHelper test) {
    var w = test.getLevel();
    int cx = (test.absolutePos(BlockPos.ZERO).getX() >> 4) + 110;
    int cz = (test.absolutePos(BlockPos.ZERO).getZ() >> 4) + 110;
    var m =
        new RestorationState.Machine(
            new BlockPos(cx * 16 + 8, 180, cz * 16 + 8), "restoration_core");
    m.reclaimed = true;
    dev.reterra.machine.RestorationEngine.advanceSpread(m, 1200);
    TestChecks.isTrue(test, 
        m.spreadRadius == 2,
        "A reclaimed habitat must reach its next chunk ring after one loaded minute.");
    BlockPos outside = new BlockPos(cx * 16 - 20, 180, cz * 16 + 8);
    w.getChunk(outside);
    w.setBlockAndUpdate(outside.below(), Blocks.STONE.defaultBlockState());
    w.setBlockAndUpdate(outside, Blocks.GRAVEL.defaultBlockState());
    w.setBlockAndUpdate(outside.above(), Blocks.AIR.defaultBlockState());
    var state = new RestorationState();
    m.rewildCursor = recoveryCursor(m.pos, 2, outside);
    dev.reterra.machine.RestorationEngine.rewild(w, state, m);
    TestChecks.isTrue(test, 
        w.getBlockState(outside).is(Blocks.DIRT)
            || w.getBlockState(outside).is(Blocks.GRASS_BLOCK),
        "Natural soil beyond the original 48 x 48 border must heal.");
    dev.reterra.machine.RestorationEngine.advanceSpread(m, 12000);
    TestChecks.isTrue(test, m.spreadRadius == 4, "Expansion must remain bounded for servers.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void revivalTakesTimeLocksItsInputsAndProducesAPair(GameTestHelper test) {
    var w = test.getLevel();
    int cx = (test.absolutePos(BlockPos.ZERO).getX() >> 4) + 130;
    int cz = (test.absolutePos(BlockPos.ZERO).getZ() >> 4) + 130;
    w.getChunk(cx, cz);
    BlockPos base = new BlockPos(cx * 16, 180, cz * 16);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        w.setBlockAndUpdate(base.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState());
        for (int y = 1; y < 6; y++)
          w.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
      }
    var core = new RestorationState.Machine(base.offset(4, 1, 4), "restoration_core");
    core.registered = true;
    for (var sector : core.sectors) {
      sector.surveyed = true;
      sector.lastSampled = 256;
      sector.lastFertile = 200;
      sector.lastGrass = 100;
      sector.lastCrops = 4;
      sector.lastWater = 4;
      sector.lastFlowers = 1;
      sector.lastTrees = 1;
      sector.lastSpecies.addAll(java.util.List.of("wheat", "oak_leaves", "dandelion"));
    }
    var chamber = new RestorationState.Machine(base.offset(10, 1, 10), "revival_chamber");
    w.setBlockAndUpdate(chamber.pos, Content.CHAMBER.defaultBlockState());
    for (var part : Multiblocks.parts(chamber.kind))
      w.setBlockAndUpdate(
          chamber.pos.offset(part.x(), part.y(), part.z()), part.block().defaultBlockState());
    chamber.setItem(3, new ItemStack(Content.CHICKEN_SAMPLE));
    chamber.setItem(4, new ItemStack(Items.WHEAT_SEEDS, 4));
    chamber.setItem(5, new ItemStack(Content.NUTRIENTS));
    chamber.water = 16;
    var state = RestorationState.get(w);
    state.machines.put(core.pos.asLong(), core);
    state.machines.put(chamber.pos.asLong(), chamber);
    var player = test.makeMockServerPlayerInLevel();
    player.snapTo(
        chamber.pos.getX(), chamber.pos.getY(), chamber.pos.getZ() + 1, 0, 0);
    dev.reterra.machine.MachineLogic.action(w, chamber, player, 1);
    TestChecks.isTrue(test, 
        !chamber.pendingSpecies.isEmpty()
            && dev.reterra.machine.RestorationEngine.animalCount(w, core) == 0,
        "Revival must start without instant animals.");
    dev.reterra.machine.MachineLogic.action(w, chamber, player, 3);
    TestChecks.isTrue(test, 
        chamber.pendingSpecies.isEmpty()
            && chamber.getItem(3).getCount() == 1
            && chamber.getItem(4).getCount() == 4
            && chamber.water == 16,
        "Cancellation must preserve all unconsumed inputs.");
    dev.reterra.machine.MachineLogic.action(w, chamber, player, 1);
    var handler =
        new dev.reterra.screen.MachineScreenHandler(2, player.getInventory(), w, chamber, false);
    TestChecks.isFalse(test, 
        handler.slots.get(3).mayPickup(player), "Incubating samples must be locked.");
    for (int i = 0; i < 79; i++) dev.reterra.machine.MachineLogic.revivalTick(w, chamber);
    TestChecks.isTrue(test, 
        dev.reterra.machine.RestorationEngine.animalCount(w, core) == 0,
        "Incubation must finish before birth.");
    dev.reterra.machine.MachineLogic.revivalTick(w, chamber);
    TestChecks.isTrue(test, 
        chamber.pendingSpecies.isEmpty()
            && chamber.getItem(3).isEmpty()
            && dev.reterra.machine.RestorationEngine.animalCount(w, core) == 2,
        "A finished cycle must produce exactly two adults and consume its sample once.");
    TestChecks.isTrue(test, 
        chamber.water == 0 && chamber.getItem(4).isEmpty() && chamber.getItem(5).isEmpty(),
        "Successful revival consumes its reserved inputs.");
    state.machines.remove(core.pos.asLong());
    state.machines.remove(chamber.pos.asLong());
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 460)
  public void allTwentyCropsCountAcrossBiomesWithinOneFastSurvey(GameTestHelper test) {
    var w = test.getLevel();
    int cx = (test.absolutePos(BlockPos.ZERO).getX() >> 4) + 150;
    int cz = (test.absolutePos(BlockPos.ZERO).getZ() >> 4) + 150;
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) {
        w.setChunkForced(cx + dx, cz + dz, true);
        w.getChunk(cx + dx, cz + dz);
      }
    BlockPos base = new BlockPos(cx * 16, 180, cz * 16);
    for (int x = 1; x <= 10; x++)
      for (int z = 1; z <= 2; z++) {
        w.setBlockAndUpdate(
            base.offset(x, 0, z),
            Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7));
        w.setBlockAndUpdate(base.offset(x, 1, z), Blocks.WHEAT.defaultBlockState());
      }
    var core = new RestorationState.Machine(base.offset(8, 1, 8), "restoration_core");
    core.registered = true;
    core.paused = true;
    core.biome = "minecraft:desert";
    w.setBlockAndUpdate(core.pos, Content.CORE.defaultBlockState());
    var state = RestorationState.get(w);
    state.machines.put(core.pos.asLong(), core);
    test.runAtTickTime(
        400,
        () -> {
          TestChecks.isTrue(test, 
              core.survey(0).crops() == 20,
              "All planted crops in the region must count, including other biome columns: "
                  + core.survey(0).crops());
          TestChecks.isTrue(test, 
              java.util.Arrays.stream(core.sectors).allMatch(s -> s.surveyed),
              "A complete loaded survey must finish in about 18 seconds.");
          state.machines.remove(core.pos.asLong());
          for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) w.setChunkForced(cx + dx, cz + dz, false);
          test.succeed();
        });
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 200)
  public void researchSitesBuildWithoutSignsWithUnattachedGenerationBlockEntities(
      GameTestHelper test) {
    ServerLevel world = test.getLevel();
    var pending = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.entity.BlockEntity>();
    int[] unattachedReads = {0};
    // Chunk generation exposes block entities before they have a World. A ServerWorld-only
    // fixture missed the SignBlockEntity.setText crash, which sends updates through that World.
    var generation =
        (net.minecraft.world.level.WorldGenLevel)
            java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {net.minecraft.world.level.WorldGenLevel.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("setBlockState")) {
                    var state = (net.minecraft.world.level.block.state.BlockState) args[1];
                    TestChecks.isFalse(test, 
                        state.getBlock() instanceof net.minecraft.world.level.block.SignBlock,
                        "ReTerra structures must not place signs.");
                    pending.remove((BlockPos) args[0]);
                  }
                  if (method.getName().equals("getBlockEntity")) {
                    BlockPos pos = ((BlockPos) args[0]).immutable();
                    if (pending.containsKey(pos)) return pending.get(pos);
                    var live = world.getBlockEntity(pos);
                    if (live == null) return null;
                    var unattached =
                        net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                            pos,
                            world.getBlockState(pos),
                            live.saveWithFullMetadata(world.registryAccess()),
                            world.registryAccess());
                    TestChecks.isTrue(test, 
                        unattached != null && unattached.getLevel() == null,
                        "Regression fixture must expose an unattached block entity.");
                    pending.put(pos, unattached);
                    unattachedReads[0]++;
                    return unattached;
                  }
                  try {
                    return method.invoke(world, args);
                  } catch (java.lang.reflect.InvocationTargetException exception) {
                    throw exception.getCause();
                  }
                });
    int cx = (test.absolutePos(BlockPos.ZERO).getX() >> 4) + 60;
    int cz = (test.absolutePos(BlockPos.ZERO).getZ() >> 4) + 60;
    for (var kind : dev.reterra.world.ResearchSites.Kind.values()) {
      BlockPos surface = new BlockPos((cx + kind.ordinal()) * 16 + 8, 90, cz * 16 + 8);
      world.getChunk(surface);
      dev.reterra.world.ResearchSites.build(generation, surface, kind);
      BlockPos chest = dev.reterra.world.ResearchSites.chestPosition(surface, kind);
      var entity = generation.getBlockEntity(chest);
      TestChecks.isTrue(test, 
          entity instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity,
          "Every site must retain its loot chest during generation: " + kind);
      var loot = (net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity) entity;
      TestChecks.isTrue(test, 
          loot.getLevel() == null
              && loot.getLootTable() != null
              && loot.getLootTable()
                  .identifier()
                  .equals(Content.id("chests/" + kind.name().toLowerCase(java.util.Locale.ROOT))),
          "Loot must be assigned safely before a generated chest is attached: " + kind);
    }
    TestChecks.isTrue(test, 
        unattachedReads[0] >= 6,
        "All six ruins must exercise the world-generation block-entity lifecycle.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 60)
  public void reclaimerConsumesOnlyAfterProducingSoil(GameTestHelper test) {
    ServerLevel w = test.getLevel();
    BlockPos p = test.absolutePos(new BlockPos(3, 2, 3));
    assemble(w, p);
    RestorationState state = RestorationState.get(w);
    RestorationState.Machine m = new RestorationState.Machine(p, "soil_reclaimer");
    m.compost = 256;
    m.fuel = 2048;
    m.water = 2048;
    state.machines.put(p.asLong(), m);
    Container inv = (Container) w.getBlockEntity(p.north());
    inv.setItem(0, new ItemStack(Items.GRAVEL));
    test.runAtTickTime(
        25,
        () -> {
          TestChecks.isTrue(test, 
              m.compost == 255 && m.fuel == 2047 && m.water == 2047,
              "One block of work uses one credit, not one whole compost or coal.");
          int dirt = m.getItem(6).is(Items.DIRT) ? m.getItem(6).getCount() : 0;
          for (int i = 0; i < inv.getContainerSize(); i++)
            if (inv.getItem(i).is(Items.DIRT)) dirt += inv.getItem(i).getCount();
          TestChecks.isTrue(test, dirt == 1, "One gravel produces one dirt; material is conserved.");
          state.machines.remove(p.asLong());
          test.succeed();
        });
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 60)
  public void blockedOutputDoesNotConsumeResources(GameTestHelper test) {
    ServerLevel w = test.getLevel();
    BlockPos p = test.absolutePos(new BlockPos(3, 2, 3));
    assemble(w, p);
    RestorationState state = RestorationState.get(w);
    RestorationState.Machine m = new RestorationState.Machine(p, "soil_reclaimer");
    m.compost = 2;
    m.fuel = 2;
    m.water = 2;
    state.machines.put(p.asLong(), m);
    Container inv = (Container) w.getBlockEntity(p.north());
    for (int i = 0; i < inv.getContainerSize(); i++) inv.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
    inv.setItem(0, new ItemStack(Items.GRAVEL, 64));
    m.setItem(6, new ItemStack(Items.DIRT, 64));
    test.runAtTickTime(
        25,
        () -> {
          TestChecks.isTrue(test, 
              m.compost == 2 && m.fuel == 2 && m.water == 2,
              "A full output must preserve supplies.");
          TestChecks.isTrue(test, 
              inv.getItem(0).getCount() == 64, "A full output must preserve input gravel.");
          state.machines.remove(p.asLong());
          test.succeed();
        });
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void persistenceIncludesMachinesScarsAndProgress(GameTestHelper test) {
    ServerLevel w = test.getLevel();
    RestorationState original = new RestorationState();
    BlockPos p = test.absolutePos(new BlockPos(1, 2, 1));
    RestorationState.Machine m = new RestorationState.Machine(p, "restoration_core");
    m.compost = 17;
    m.water = 32;
    m.reclaimed = true;
    m.sectors[2].cursor = 147;
    m.sectors[2].species.add("minecraft:oak_leaves");
    original.reclaimedRegions.put(p.asLong(), m);
    var replacement = new RestorationState.Machine(p, "water_restorer");
    replacement.water = 96;
    original.machines.put(p.asLong(), replacement);
    original.protectedBlocks.add(p.above().asLong());
    CompoundTag data = original.writeNbt(new CompoundTag(), w.registryAccess());
    RestorationState copy = RestorationState.fromNbt(data, w.registryAccess());
    var restored = copy.reclaimedRegions.get(p.asLong());
    TestChecks.isTrue(test, 
        copy.machines.get(p.asLong()).water == 96,
        "Reclaimed regions persist independently of a new machine at the same anchor.");
    TestChecks.isTrue(test, 
        restored.compost == 17 && restored.water == 32 && restored.reclaimed,
        "Machine resources and completion survive saving.");
    TestChecks.isTrue(test, 
        restored.sectors[2].cursor == 147
            && restored.sectors[2].species.contains("minecraft:oak_leaves"),
        "Partial sector surveys survive saving.");
    TestChecks.isTrue(test, 
        copy.protectedBlocks.contains(p.above().asLong()), "Player block protection survives saving.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void dataRecipesAndLootAreLoaded(GameTestHelper test) {
    var manager = test.getLevel().recipeAccess();
    TestChecks.isTrue(test, 
        TestAccess.recipe(test.getLevel(), "recovery_bundle").isEmpty(),
        "Seed pouches must not have a crafting shortcut.");
    TestChecks.isTrue(test, 
        TestAccess.recipe(test.getLevel(), "restoration_core").isPresent(), "Core recipe must load.");
    var core = TestAccess.recipe(test.getLevel(), "restoration_core").orElseThrow().value();
    TestChecks.isTrue(test, 
        core.placementInfo().ingredients().stream().anyMatch(i -> i.test(new ItemStack(Items.REDSTONE_BLOCK)))
            && core.placementInfo().ingredients().stream()
                .noneMatch(i -> i.test(new ItemStack(Items.LODESTONE))),
        "The Core must use a vanilla redstone block instead of a lodestone.");
    for (String shortcut : java.util.List.of("restored_moss", "lodestone"))
      TestChecks.isTrue(test, 
          TestAccess.recipe(test.getLevel(), shortcut).isEmpty(),
          "Vanilla materials must not need the removed " + shortcut + " recipe.");
    TestChecks.isTrue(test, 
        TestAccess.recipe(test.getLevel(), "bee_sample").isEmpty(),
        "Biological samples must come from preserved specimens, not stones and food.");
    TestChecks.isTrue(test, 
        TestAccess.recipe(test.getLevel(), "starter_beehive").isEmpty(),
        "Empty starter hives are salvaged from greenhouses.");
    TestChecks.isTrue(test, 
        test.getLevel().getServer().getAdvancements().get(Content.id("new_beginning")) != null,
        "Endgame advancement must load.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 60)
  public void terrainModePreservesPlacedBlocksAndScars(GameTestHelper test) {
    ServerLevel w = test.getLevel();
    // GameTest encloses templates in a roof; restoration deliberately targets outdoor surfaces.
    BlockPos p = test.absolutePos(new BlockPos(3, 12, 3));
    for (int x = 0; x < 8; x++)
      for (int z = 0; z < 8; z++)
        w.setBlockAndUpdate(
            test.absolutePos(new BlockPos(x, 11, z)), Blocks.COARSE_DIRT.defaultBlockState());
    assemble(w, p);
    RestorationState state = RestorationState.get(w);
    BlockPos protectedSoil = p.east().below();
    state.protectedBlocks.add(protectedSoil.asLong());
    BlockPos natural = null;
    for (int dx = 2; dx <= 4; dx++)
      if (!Ecology.isScar(p.getX() + dx, p.getZ())) {
        natural = p.east(dx).below();
        break;
      }
    if (natural == null) throw new AssertionError("No non-scar column in fixture.");
    BlockPos target = natural;
    RestorationState.Machine m = new RestorationState.Machine(p, "soil_reclaimer");
    m.production = false;
    m.cursor = 110;
    m.compost = 256;
    m.fuel = 2048;
    m.water = 2048;
    state.machines.put(p.asLong(), m);
    test.runAtTickTime(
        25,
        () -> {
          TestChecks.isTrue(test, 
              w.getBlockState(protectedSoil).is(Blocks.COARSE_DIRT),
              "Machine must not replace a player-placed soil block.");
          TestChecks.isTrue(test, 
              w.getBlockState(target).is(Blocks.DIRT),
              "Natural soil should restore: target="
                  + target
                  + ", ground="
                  + dev.reterra.machine.RestorationEngine.ground(w, target.getX(), target.getZ())
                  + ", state="
                  + w.getBlockState(target)
                  + ", cursor="
                  + m.cursor
                  + ", compost="
                  + m.compost
                  + ", structure="
                  + Multiblocks.missing(w, p));
          state.machines.remove(p.asLong());
          state.protectedBlocks.remove(protectedSoil.asLong());
          test.succeed();
        });
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void barrenSpawnsDoNotBlockBreedingOrRevival(GameTestHelper test) {
    ServerLevel w = test.getLevel();
    var natural = TestAccess.create(dev.reterra.compat.GameAccess.SHEEP, w);
    natural.finalizeSpawn(
        w,
        w.getCurrentDifficultyAt(test.absolutePos(BlockPos.ZERO)),
        net.minecraft.world.entity.EntitySpawnReason.NATURAL,
        null);
    TestChecks.isTrue(test, 
        natural.entityTags().contains("reterra_barren_spawn"),
        "Naturally spawned wildlife is suppressed.");
    var revived = TestAccess.create(dev.reterra.compat.GameAccess.SHEEP, w);
    revived.finalizeSpawn(
        w,
        w.getCurrentDifficultyAt(test.absolutePos(BlockPos.ZERO)),
        net.minecraft.world.entity.EntitySpawnReason.BREEDING,
        null);
    TestChecks.isFalse(test, 
        revived.entityTags().contains("reterra_barren_spawn"), "Breeding must stay available.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void reclaimedBiomesPersistAndPreserveOtherBiomeIdentities(GameTestHelper test) {
    ServerLevel w = test.getLevel();
    BlockPos p = test.absolutePos(new BlockPos(3, 2, 3));
    var m = new RestorationState.Machine(p, "restoration_core");
    m.reclaimed = true;
    m.biomeCursor = 4;
    m.biome = w.getBiome(p).unwrapKey().orElseThrow().identifier().toString();
    int x = (p.getX() >> 4) * 16 + 8, z = (p.getZ() >> 4) * 16 + 8;
    var forest =
        w.registryAccess()
            .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
            .get(net.minecraft.resources.Identifier.withDefaultNamespace("forest"))
            .orElseThrow();
    BlockPos forestColumn = new BlockPos((x >> 4) * 16, 64, (z >> 4) * 16);
    net.minecraft.server.commands.FillBiomeCommand.fill(w, forestColumn, forestColumn, forest);
    dev.reterra.world.ReclaimedBiomes.tick(w, m);
    TestChecks.isTrue(test, 
        m.sectors[4].biomeTransitioned, "A loaded sector must finish its biome transition.");
    var biome = w.getChunk(x >> 4, z >> 4).getNoiseBiome(x >> 2, 64 >> 2, z >> 2);
    TestChecks.isTrue(test, 
        biome.unwrapKey().orElseThrow().identifier().toString().equals("reterra:reclaimed_plains"),
        "Completed regions must use a distinct reclaimed biome.");
    TestChecks.isTrue(test, 
        w.getChunk(x >> 4, z >> 4)
            .getNoiseBiome(forestColumn.getX() >> 2, 16, forestColumn.getZ() >> 2)
            .is(net.minecraft.resources.Identifier.withDefaultNamespace("forest")),
        "A neighboring biome identity inside the same chunk must remain untouched.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void generationLeavesDeadWoodAndRemovesLivingSurface(GameTestHelper test) {
    ServerLevel w = test.getLevel();
    BlockPos anchor = test.absolutePos(new BlockPos(4, 2, 4));
    // The trunk and its leaves must stay inside the chunk this test transforms.
    BlockPos p = (anchor.getX() & 15) == 15 ? anchor.west() : anchor;
    w.setBlockAndUpdate(p, Blocks.GRASS_BLOCK.defaultBlockState());
    w.setBlockAndUpdate(p.above(), Blocks.DANDELION.defaultBlockState());
    w.setBlockAndUpdate(p.east(), Blocks.OAK_LOG.defaultBlockState());
    w.setBlockAndUpdate(p.east().above(), Blocks.OAK_LEAVES.defaultBlockState());
    dev.reterra.world.BarrenWorld.transform(w, w.getChunk(p));
    TestChecks.isFalse(test, 
        w.getBlockState(p).is(Blocks.GRASS_BLOCK), "Generated surface must be barren.");
    TestChecks.isTrue(test, 
        w.getBlockState(p.above()).isAir() && w.getBlockState(p.east().above()).isAir(),
        "Generated flowers and leaves must be removed.");
    TestChecks.isTrue(test, 
        w.getBlockState(p.east()).is(Blocks.OAK_LOG),
        "Dead trees must still provide normal wood.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void realBlockPlacementIsProtected(GameTestHelper test) {
    ServerLevel w = test.getLevel();
    BlockPos base = test.absolutePos(new BlockPos(3, 2, 3));
    w.setBlockAndUpdate(base, Blocks.STONE.defaultBlockState());
    w.setBlockAndUpdate(base.above(), Blocks.AIR.defaultBlockState());
    var player = test.makeMockServerPlayerInLevel();
    var hit =
        new net.minecraft.world.phys.BlockHitResult(
            net.minecraft.world.phys.Vec3.atCenterOf(base),
            net.minecraft.core.Direction.UP,
            base,
            false);
    var placement =
        new net.minecraft.world.item.context.BlockPlaceContext(
            w, player, net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT), hit);
    var result = ((net.minecraft.world.item.BlockItem) Items.DIRT).place(placement);
    TestChecks.isTrue(test, result.consumesAction(), "Fixture must place a real block item.");
    var state = RestorationState.get(w);
    TestChecks.isTrue(test, 
        state.protectedBlocks.contains(base.above().asLong()),
        "The placement hook must protect the actual destination block.");
    state.protectedBlocks.remove(base.above().asLong());
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 200)
  public void everyResearchSiteBuildsWithUsableLoot(GameTestHelper test) {
    ServerLevel w = test.getLevel();
    int cx = (test.absolutePos(BlockPos.ZERO).getX() >> 4) + 16;
    int cz = (test.absolutePos(BlockPos.ZERO).getZ() >> 4) + 16;
    for (var kind : dev.reterra.world.ResearchSites.Kind.values()) {
      int x = (cx + kind.ordinal()) * 16 + 8, z = cz * 16 + 8;
      w.getChunk(x >> 4, z >> 4);
      BlockPos surface = new BlockPos(x, 80, z);
      dev.reterra.world.ResearchSites.build(w, surface, kind);
      BlockPos chest = dev.reterra.world.ResearchSites.chestPosition(surface, kind);
      TestChecks.isTrue(test, 
          w.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity,
          "Site must contain a chest: " + kind);
      var inventory = (net.minecraft.world.Container) w.getBlockEntity(chest);
      boolean supplies = false;
      for (int i = 0; i < inventory.getContainerSize(); i++)
        if (!inventory.getItem(i).isEmpty()) supplies = true;
      TestChecks.isTrue(test, supplies, "Site loot table must generate resources: " + kind);
      int pouches = 0, cultures = 0, samples = 0;
      for (int i = 0; i < inventory.getContainerSize(); i++) {
        var stack = inventory.getItem(i);
        if (stack.is(Content.RECOVERY_BUNDLE)) pouches += stack.getCount();
        if (stack.is(Content.CULTURE)) cultures += stack.getCount();
        if (dev.reterra.machine.MachineLogic.sample(stack)) samples += stack.getCount();
      }
      boolean seedSite =
          kind == dev.reterra.world.ResearchSites.Kind.FARMHOUSE
              || kind == dev.reterra.world.ResearchSites.Kind.SEED_VAULT;
      TestChecks.isTrue(test, 
          pouches == (seedSite ? 1 : 0),
          "Only farmhouses and seed vaults should contain one seed pouch.");
      TestChecks.isTrue(test, 
          cultures == (kind == dev.reterra.world.ResearchSites.Kind.GREENHOUSE ? 1 : 0),
          "Living cultures belong in greenhouses, not every random chest.");
      TestChecks.isTrue(test, 
          samples == (kind == dev.reterra.world.ResearchSites.Kind.BIOLOGICAL_ARCHIVE ? 1 : 0),
          "Each archive preserves one sample, so one chest cannot finish wildlife progression.");
      int circuits = 0;
      for (int i = 0; i < inventory.getContainerSize(); i++)
        if (inventory.getItem(i).is(Content.FRAGMENT))
          circuits += inventory.getItem(i).getCount();
      TestChecks.isTrue(test, 
          circuits == (kind == dev.reterra.world.ResearchSites.Kind.RESEARCH_STATION ? 1 : 0),
          "Control circuits belong to Research Stations, separate from preserved samples.");
      TestChecks.isFalse(test, 
          w.getBlockState(surface).is(Content.CORE)
              || w.getBlockState(surface).is(Content.CHAMBER),
          "Ruins must not give away complete machines.");
      if (kind == dev.reterra.world.ResearchSites.Kind.GREENHOUSE)
        TestChecks.isTrue(test, 
            w.getBlockState(surface.offset(-4, 0, 3)).is(Blocks.BEEHIVE),
            "Greenhouses must supply an empty hive before the first honeycomb exists.");
    }
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void joiningDoesNotGiveAnyItems(GameTestHelper test) {
    var player = test.makeMockServerPlayerInLevel();
    player.getInventory().clearContent();
    ReTerra.onJoin(player);
    ReTerra.onJoin(player);
    TestChecks.isTrue(test, 
        player.getInventory().isEmpty(), "First join and reconnect must leave inventory empty.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void seedPouchContainsOnlyOneSmallPlantingStart(GameTestHelper test) {
    var player = test.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
    TestChecks.isFalse(test, player.isCreative(), "Seed-pouch fixture must use survival consumption.");
    player.getInventory().clearContent();
    player.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Content.RECOVERY_BUNDLE));
    ReTerra.openBundle(player, player.getMainHandItem());
    int seeds = 0, mushrooms = 0, total = 0;
    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
      var stack = player.getInventory().getItem(i);
      total += stack.getCount();
      if (stack.is(Items.WHEAT_SEEDS)) seeds += stack.getCount();
      if (stack.is(Items.BROWN_MUSHROOM)) mushrooms += stack.getCount();
    }
    TestChecks.isTrue(test, 
        seeds == 2 && mushrooms == 1 && total == 3,
        "Pouch contains exactly 2 wheat seeds and 1 mushroom; no gear, saplings or water.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void hoeCannotProduceDirtFromSterileSoil(GameTestHelper test) {
    var w = test.getLevel();
    var pos = test.absolutePos(new BlockPos(3, 2, 3));
    w.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
    var player = test.makeMockServerPlayerInLevel();
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_HOE));
    var hit =
        new net.minecraft.world.phys.BlockHitResult(
            net.minecraft.world.phys.Vec3.atCenterOf(pos),
            net.minecraft.core.Direction.UP,
            pos,
            false);
    // Exercise the same interaction path as a player, including Fabric callbacks.
    for (var soil : new net.minecraft.world.level.block.Block[] {Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT}) {
      w.setBlockAndUpdate(pos, soil.defaultBlockState());
      var result = player.gameMode.useItemOn(
          player, w, player.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
      TestChecks.isTrue(test,
          result == net.minecraft.world.InteractionResult.FAIL && w.getBlockState(pos).is(soil),
          "Hoe must not bypass restoration.");
      TestChecks.isTrue(test,
          player.getMainHandItem().getDamageValue() == 0, "Blocked tilling must not damage the hoe.");
    }
    w.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
    var result = player.gameMode.useItemOn(
        player, w, player.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
    TestChecks.isTrue(test,
        result.consumesAction() && w.getBlockState(pos).is(Blocks.FARMLAND),
        "Normal dirt must still turn into farmland.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void generationAlsoSterilizesBuriedDirt(GameTestHelper test) {
    var w = test.getLevel();
    var pos = test.absolutePos(new BlockPos(4, 6, 4));
    w.setBlockAndUpdate(pos, Blocks.COARSE_DIRT.defaultBlockState());
    w.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
    w.setBlockAndUpdate(pos.below(3), Blocks.ROOTED_DIRT.defaultBlockState());
    dev.reterra.world.BarrenWorld.transform(w, w.getChunk(pos));
    TestChecks.isFalse(test, 
        w.getBlockState(pos.below()).is(Blocks.DIRT),
        "There must not be a free fertile second layer.");
    TestChecks.isTrue(test, 
        w.getBlockState(pos.below(3)).is(Blocks.COARSE_DIRT)
            || w.getBlockState(pos.below(3)).is(Blocks.GRAVEL),
        "Deep rooted dirt must also be sterile.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void machineSlotsPersistAndShiftClickCannotDuplicateOrInsertOutput(GameTestHelper test) {
    var w = test.getLevel();
    var pos = test.absolutePos(new BlockPos(3, 2, 3));
    assemble(w, pos);
    var state = RestorationState.get(w);
    var m = new RestorationState.Machine(pos, "soil_reclaimer");
    m.dirty = state::setDirty;
    state.machines.put(pos.asLong(), m);
    var player = test.makeMockServerPlayerInLevel();
    player.snapTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + 1.5, 0, 0);
    player.getInventory().clearContent();
    var handler =
        new dev.reterra.screen.MachineScreenHandler(1, player.getInventory(), w, m, false);
    player.getInventory().setItem(0, new ItemStack(Items.DIRT, 5));
    TestChecks.isTrue(test, 
        handler.quickMoveStack(player, 35).isEmpty(), "Dirt must not be inserted as fuel or output.");
    player.getInventory().setItem(0, new ItemStack(Items.GRAVEL, 5));
    handler.quickMoveStack(player, 35);
    TestChecks.isTrue(test, 
        m.getItem(3).is(Items.GRAVEL)
            && m.getItem(3).getCount() == 5
            && player.getInventory().getItem(0).isEmpty(),
        "Shift-click must transfer gravel exactly once.");
    TestChecks.isFalse(test, 
        handler.slots.get(6).mayPlace(new ItemStack(Items.DIRT)),
        "Output slot is extraction-only.");
    var saved = state.writeNbt(new CompoundTag(), w.registryAccess());
    var restored = RestorationState.fromNbt(saved, w.registryAccess());
    TestChecks.isTrue(test, 
        restored.machines.get(pos.asLong()).getItem(3).getCount() == 5,
        "Inserted items must survive saving and reload.");
    m.setItem(6, new ItemStack(Items.DIRT, 4));
    handler.quickMoveStack(player, 6);
    TestChecks.isTrue(test, m.getItem(6).isEmpty(), "Shift-click extracts the output once.");
    int dirt = 0;
    for (int i = 0; i < player.getInventory().getContainerSize(); i++)
      if (player.getInventory().getItem(i).is(Items.DIRT))
        dirt += player.getInventory().getItem(i).getCount();
    TestChecks.isTrue(test, dirt == 4, "No duplicate items after output extraction.");
    boolean before = m.production;
    player.snapTo(pos.getX() + 100.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
    TestChecks.isFalse(test, 
        handler.clickMenuButton(player, 0),
        "A forged button packet must not operate a distant machine.");
    TestChecks.isTrue(test, m.production == before, "Rejected remote control must preserve machine mode.");
    state.machines.remove(pos.asLong());
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void brokenMachineDropsStoredItemsAndRemovesItsRecord(GameTestHelper test) {
    var w = test.getLevel();
    var pos = test.absolutePos(new BlockPos(3, 2, 3));
    assemble(w, pos);
    var state = RestorationState.get(w);
    var m = new RestorationState.Machine(pos, "soil_reclaimer");
    m.setItem(0, new ItemStack(Content.COMPOST, 3));
    state.machines.put(pos.asLong(), m);
    w.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    TestChecks.isFalse(test, 
        state.machines.containsKey(pos.asLong()),
        "Destroyed machines must not retain live inventories.");
    int count =
        w
            .getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(2),
                item -> item.getItem().is(Content.COMPOST))
            .stream()
            .mapToInt(item -> item.getItem().getCount())
            .sum();
    TestChecks.isTrue(test, count == 3, "Stored supplies must be dropped exactly once.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 60)
  public void idleCoreDoesNotBurnCoalOrCompost(GameTestHelper test) {
    var w = test.getLevel();
    var pos = test.absolutePos(new BlockPos(3, 2, 3));
    w.setBlockAndUpdate(pos, Content.CORE.defaultBlockState());
    w.setBlockAndUpdate(pos.below(), Blocks.FURNACE.defaultBlockState());
    for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL)
      w.setBlockAndUpdate(pos.relative(direction), Blocks.POLISHED_ANDESITE.defaultBlockState());
    w.setBlockAndUpdate(pos.north(2), Blocks.WATER_CAULDRON.defaultBlockState());
    var state = RestorationState.get(w);
    var m = new RestorationState.Machine(pos, "restoration_core");
    m.registered = false;
    m.biome = "minecraft:the_void";
    m.compost = 256;
    m.fuel = 2048;
    m.water = 2048;
    state.machines.put(pos.asLong(), m);
    test.runAtTickTime(
        25,
        () -> {
          TestChecks.isTrue(test, 
              m.compost == 256 && m.fuel == 2048 && m.water == 2048,
              "Survey-only ticks must consume no work credits.");
          state.machines.remove(pos.asLong());
          test.succeed();
        });
  }

  private static void assemble(ServerLevel w, BlockPos p) {
    w.setBlockAndUpdate(p, Content.RECLAIMER.defaultBlockState());
    w.setBlockAndUpdate(p.below(), Blocks.FURNACE.defaultBlockState());
    w.setBlockAndUpdate(p.north(), Blocks.BARREL.defaultBlockState());
    w.setBlockAndUpdate(
        p.south(), Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
    if (Multiblocks.missing(w, p) != null)
      throw new AssertionError("Valid reclaimer multiblock was rejected.");
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void healthyTrialCannotBeSkippedByUnloadingAndProgressPersists(GameTestHelper test) {
    var m = new RestorationState.Machine(test.absolutePos(BlockPos.ZERO), "restoration_core");
    for (int tick = 20; tick <= 3620; tick += 20)
      dev.reterra.machine.RestorationEngine.updateStability(m, true, tick);
    TestChecks.isTrue(test, 
        m.stableSeconds == 180, "Three minutes of healthy loaded time completes the trial.");
    m.stableSeconds = 170;
    dev.reterra.machine.RestorationEngine.updateStability(m, false, 3640);
    TestChecks.isTrue(test, 
        m.stableSeconds == 170, "A momentary habitat loss must preserve earned progress.");
    dev.reterra.machine.RestorationEngine.updateStability(m, true, 3660);
    dev.reterra.machine.RestorationEngine.updateStability(m, true, 3680);
    TestChecks.isTrue(test, 
        m.stableSeconds == 173, "Healthy loaded time must resume from existing progress.");
    dev.reterra.machine.RestorationEngine.updateStability(m, true, 8000);
    TestChecks.isTrue(test, 
        m.stableSeconds == 173,
        "Time spent unloaded must pause, without completing or erasing progress.");
    for (int tick = 8020; tick <= 8120; tick += 20)
      dev.reterra.machine.RestorationEngine.updateStability(m, false, tick);
    TestChecks.isTrue(test, 
        m.stableSeconds == 172,
        "A deteriorating loaded habitat loses one second per five observed seconds.");
    m.stableSeconds = 73;
    m.rewildCursor = 1900;
    var state = new RestorationState();
    state.machines.put(m.pos.asLong(), m);
    var restored =
        RestorationState.fromNbt(
            state.writeNbt(new CompoundTag(), test.getLevel().registryAccess()),
            test.getLevel().registryAccess());
    var copy = restored.machines.get(m.pos.asLong());
    TestChecks.isTrue(test, 
        copy.stableSeconds == 73 && copy.rewildCursor == 1900,
        "Trial and passive succession progress must survive saves.");
    test.succeed();
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 120)
  public void completedRegionHealsScarsAndRegrowsOnlyUnprotectedTrunks(GameTestHelper test) {
    ServerLevel w = test.getLevel();
    TestAccess.time(w, 6000);
    int cx = (test.absolutePos(BlockPos.ZERO).getX() >> 4) + 35;
    int cz = (test.absolutePos(BlockPos.ZERO).getZ() >> 4) + 35;
    w.setChunkForced(cx, cz, true);
    w.getChunk(cx, cz);
    BlockPos origin = new BlockPos(cx * 16, 180, cz * 16);
    var m = new RestorationState.Machine(origin.offset(8, 1, 8), "restoration_core");
    m.reclaimed = true;
    m.biome = w.getBiome(origin).unwrapKey().orElseThrow().identifier().toString();
    var state = new RestorationState();
    state.reclaimedRegions.put(m.pos.asLong(), m);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        w.setBlockAndUpdate(origin.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState());
        for (int y = 1; y < 9; y++)
          w.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
      }
    BlockPos scar = origin.offset(2, 0, 2), protectedSoil = origin.offset(3, 0, 3);
    w.setBlockAndUpdate(scar.below(), Blocks.STONE.defaultBlockState());
    w.setBlockAndUpdate(protectedSoil.below(), Blocks.STONE.defaultBlockState());
    w.setBlockAndUpdate(scar, Blocks.GRAVEL.defaultBlockState());
    w.setBlockAndUpdate(protectedSoil, Blocks.GRAVEL.defaultBlockState());
    state.protectedBlocks.add(protectedSoil.asLong());
    BlockPos trunk = origin.offset(5, 1, 5), protectedTrunk = origin.offset(11, 1, 11);
    for (int y = 0; y < 4; y++) {
      w.setBlockAndUpdate(trunk.above(y), Blocks.OAK_LOG.defaultBlockState());
      w.setBlockAndUpdate(protectedTrunk.above(y), Blocks.OAK_LOG.defaultBlockState());
      state.protectedBlocks.add(protectedTrunk.above(y).asLong());
    }
    test.runAtTickTime(
        30,
        () -> {
          for (int i = 0; i < 350; i++) dev.reterra.machine.RestorationEngine.rewild(w, state, m);
          for (int i = 0; i < 80; i++) dev.reterra.world.TreeRecovery.tick(w, state);
          TestChecks.isTrue(test, 
              w.getBlockState(scar).is(Blocks.DIRT)
                  || w.getBlockState(scar).is(Blocks.GRASS_BLOCK),
              "A completed region must reclaim sterile scar soil.");
        });
    // Lighting is asynchronous. Give terrain and crown changes real ticks before the grass pass.
    test.runAtTickTime(
        60,
        () -> {
          for (int i = 0; i < 350; i++) dev.reterra.machine.RestorationEngine.rewild(w, state, m);
          for (int i = 0; i < 80; i++) dev.reterra.world.TreeRecovery.tick(w, state);
          TestChecks.isTrue(test, 
              w.getBlockState(scar).is(Blocks.GRASS_BLOCK),
              "Even scar gravel should heal after completion: "
                  + w.getBlockState(scar)
                  + ", loaded="
                  + dev.reterra.machine.RestorationEngine.loaded(w, scar)
                  + ", ground="
                  + dev.reterra.machine.RestorationEngine.ground(w, scar.getX(), scar.getZ())
                  + ", biome="
                  + w.getBiome(scar).unwrapKey()
                  + ", expected="
                  + m.biome
                  + ", light="
                  + w.getMaxLocalRawBrightness(scar.above())
                  + ", day="
                  + w.getOverworldClockTime());
          TestChecks.isTrue(test, 
              w.getBlockState(protectedSoil).is(Blocks.GRAVEL),
              "Player gravel must survive succession.");
          TestChecks.isTrue(test, 
              w.getBlockState(trunk.west().above(3)).is(Blocks.OAK_LEAVES),
              "Natural dead trunks should regrow a crown.");
          TestChecks.isTrue(test, 
              w.getBlockState(protectedTrunk.above(4)).isAir(),
              "Player log pillars must not grow leaves.");
          TestChecks.isTrue(test, 
              m.compost == 0 && m.fuel == 0 && m.water == 0,
              "Recovered ecosystems need no machine supplies.");
          w.setChunkForced(cx, cz, false);
          test.succeed();
        });
  }

  private static int recoveryCursor(BlockPos center, int radius, BlockPos target) {
    int count = (radius * 2 + 1) * (radius * 2 + 1) * 256;
    for (int i = 0; i < count; i++) {
      BlockPos p = dev.reterra.world.RecoveryPattern.column(center, radius, i);
      if (p.getX() == target.getX() && p.getZ() == target.getZ()) return i;
    }
    throw new IllegalArgumentException("Target outside recovery area");
  }
  @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 160)
  public void locateCommandFindsAnActualArchiveAndSupportsVanillaSyntax(GameTestHelper test) {
    ServerLevel w = test.getLevel();
    var from = test.absolutePos(BlockPos.ZERO).offset(3000, 0, 3000);
    var kind = dev.reterra.world.ResearchSites.Kind.BIOLOGICAL_ARCHIVE;
    BlockPos candidate =
        dev.reterra.world.SiteLocator.candidates(w.getSeed(), from, kind).getFirst();
    w.getChunk(candidate.getX() >> 4, candidate.getZ() >> 4);
    BlockPos surface =
        candidate.atY(
            w.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                    candidate.getX(),
                    candidate.getZ())
                + 1);
    dev.reterra.world.ResearchSites.build(w, surface, kind);
    BlockPos chest = dev.reterra.world.ResearchSites.chestPosition(surface, kind);
    TestChecks.isTrue(test, 
        chest.equals(dev.reterra.world.SiteLocator.findChest(w, candidate, kind)),
        "Locator must find the real chest, including its actual elevation.");
    var output = new StringBuilder();
    var sink =
        new net.minecraft.commands.CommandSource() {
          public void sendSystemMessage(net.minecraft.network.chat.Component text) {
            output.append(text.getString()).append('\n');
          }

          public boolean acceptsSuccess() {
            return true;
          }

          public boolean acceptsFailure() {
            return true;
          }

          public boolean shouldInformAdmins() {
            return false;
          }
        };
    var source =
        w.getServer()
            .createCommandSourceStack()
            .withLevel(w)
            .withPosition(net.minecraft.world.phys.Vec3.atCenterOf(surface))
            .withSource(sink);
    var dispatcher = w.getServer().getCommands().getDispatcher();
    for (String command :
        new String[] {
          "locate reterra biological_archive",
          "locate structure reterra:biological_archive",
          "reterra locate biological_archive"
        })
      TestChecks.isTrue(test, 
          dispatcher.parse(command, source).getReader().getRemaining().isEmpty(),
          "Command must parse: " + command);
    dev.reterra.world.SiteLocator.clear();
    TestAccess.execute(source, "locate structure reterra:biological_archive");
    test.runAtTickTime(
        80,
        () -> {
          TestChecks.isTrue(test, 
              output
                  .toString()
                  .contains(
                      "found at [" + chest.getX() + ", " + chest.getY() + ", " + chest.getZ()),
              "Asynchronous command must report an existing Archive, not an unchecked prediction: "
                  + output);
          dev.reterra.world.SiteLocator.clear();
          test.succeed();
        });
  }
}
