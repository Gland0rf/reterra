package dev.reterra;

import dev.reterra.ecology.Ecology;
import dev.reterra.machine.Multiblocks;
import dev.reterra.state.RestorationState;
//? if <1.21.5 {
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
//? }
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
//? if <1.21.5 {
import net.minecraft.test.GameTest;
//? } else {
/*import net.fabricmc.fabric.api.gametest.v1.GameTest;
*///? }
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

//? if <1.21.5 {
public class RestorationGameTests implements FabricGameTest {
//? } else {
/*public class RestorationGameTests {
  private static final String EMPTY_STRUCTURE = "fabric-gametest-api-v1:empty";
*///? }
  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void riverGenerationLeavesOneConsistentRemnantWaterline(TestContext test) {
    var water = Blocks.WATER.getDefaultState();
    for (int x = -8; x <= 8; x++)
      for (int z = -8; z <= 8; z++)
        for (int y = 53; y <= 62; y++) {
          var result = dev.reterra.world.BarrenWorld.barrenState(new BlockPos(x, y, z), water, true, 54);
          TestChecks.isTrue(test, y > 54 ? result.isAir() : result.isOf(Blocks.WATER),
              "River columns must share a low waterline, with no full-height random pools.");
        }
    var ocean = dev.reterra.world.BarrenWorld.barrenState(new BlockPos(0, 62, 0), water, false, 54);
    TestChecks.isTrue(test, ocean.equals(water), "Oceans and ordinary lakes must retain their water.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void underwaterPlantRemovalPreservesWaterAndDrainedRootsDoNotLeak(TestContext test) {
    var surface = new BlockPos(0, 62, 0);
    var seagrass = Blocks.SEAGRASS.getDefaultState();
    TestChecks.isTrue(test,
        dev.reterra.world.BarrenWorld.barrenState(surface, seagrass, false, 54).isOf(Blocks.WATER),
        "Stripping underwater vegetation must not punch holes in ocean or lake water.");
    TestChecks.isTrue(test,
        dev.reterra.world.BarrenWorld.barrenState(surface, seagrass, true, 54).isAir(),
        "Plants in a dry river must not leave floating source blocks.");
    var roots = Blocks.MANGROVE_ROOTS.getDefaultState()
        .with(net.minecraft.state.property.Properties.WATERLOGGED, true);
    var dry = dev.reterra.world.BarrenWorld.barrenState(surface, roots, true, 54);
    TestChecks.isTrue(test, dry.isOf(Blocks.MANGROVE_ROOTS)
        && !dry.get(net.minecraft.state.property.Properties.WATERLOGGED),
        "Keep the old roots but remove water above a drained river's waterline.");
    TestChecks.isTrue(test,
        dev.reterra.world.BarrenWorld.barrenState(surface, Blocks.ICE.getDefaultState(), true, 54).isAir(),
        "Frozen rivers must not retain floating ice over dry channels.");
    TestChecks.isTrue(test,
        dev.reterra.world.BarrenWorld.barrenState(surface, Blocks.ICE.getDefaultState(), false, 54).isOf(Blocks.ICE),
        "Ice outside drained rivers must remain intact.");
    test.complete();
  }

  //? if >=1.21.11 {
  /*@net.fabricmc.fabric.api.gametest.v1.GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  public void legacyTerrainChoiceSurvivesGameRuleMigration(TestContext test) {
    for (boolean barren : new boolean[] {false, true}) {
      var level = new NbtCompound();
      var rules = new NbtCompound();
      rules.putString("reterraBarrenWorld", Boolean.toString(barren));
      rules.putString("keepInventory", "true");
      level.put("GameRules", rules);
      var fixed = net.minecraft.datafixer.DataFixTypes.LEVEL.update(
          test.getWorld().getServer().getDataFixer(), level, 3953);
      var migrated = fixed.getCompoundOrEmpty("game_rules");
      TestChecks.isTrue(test, migrated.getBoolean("reterra:barren_world", !barren) == barren,
          "Upgrading must preserve the saved terrain choice.");
      TestChecks.isTrue(test, migrated.getBoolean("minecraft:keep_inventory", false),
          "The ReTerra migration must preserve vanilla game rules too.");
    }
    test.complete();
  }
  *///? }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void earlyMaterialsRevealTheWholeMachineRecipeChain(TestContext test) {
    var player = test.createMockCreativeServerPlayerInWorld();
    var iron = new ItemStack(Items.IRON_INGOT);
    player.getInventory().setStack(0, iron);
    net.minecraft.advancement.criterion.Criteria.INVENTORY_CHANGED.trigger(
        player, player.getInventory(), iron);
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
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void tanksAcceptASecondBucketAndNeverOverflow(TestContext test) {
    for (String kind : dev.reterra.machine.MachineLogic.KINDS.subList(0, 4)) {
      var machine = new RestorationState.Machine(BlockPos.ORIGIN, kind);
      machine.water = 128;
      machine.setStack(2, new ItemStack(Items.WATER_BUCKET));
      dev.reterra.machine.MachineLogic.stock(machine);
      TestChecks.isTrue(test, 
          machine.water == 256 && machine.getStack(2).isOf(Items.BUCKET),
          "A half-full " + kind + " must accept a second bucket in the same slot.");
      machine.setStack(2, new ItemStack(Items.WATER_BUCKET));
      dev.reterra.machine.MachineLogic.stock(machine);
      TestChecks.isTrue(test, 
          machine.water == 256 && machine.getStack(2).isOf(Items.WATER_BUCKET),
          "Full tanks must leave the next water bucket untouched.");
      machine.water = 129;
      dev.reterra.machine.MachineLogic.stock(machine);
      TestChecks.isTrue(test, 
          machine.water == 129 && machine.getStack(2).isOf(Items.WATER_BUCKET),
          "A bucket must wait until there is room for all 128 units.");
    }
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void chamberFoodSlotsFollowTheSelectedSpecies(TestContext test) {
    var player = test.createMockCreativeServerPlayerInWorld();
    var chamber =
        new RestorationState.Machine(
            test.getAbsolutePos(new BlockPos(2, 12, 2)), "revival_chamber");
    var handler =
        new dev.reterra.screen.MachineScreenHandler(
            1, player.getInventory(), test.getWorld(), chamber, false);
    handler.onButtonClick(player, 10);
    for (var sample :
        java.util.List.of(
            Content.COW_SAMPLE, Content.SHEEP_SAMPLE, Content.CHICKEN_SAMPLE, Content.BEE_SAMPLE)) {
      chamber.setStack(3, new ItemStack(sample));
      var expected = dev.reterra.machine.MachineLogic.food(chamber.getStack(3));
      TestChecks.isTrue(test, 
          handler.getSlot(4).canInsert(new ItemStack(expected)),
          "Food slot must accept the selected animal's food.");
      var wrong = expected == Items.WHEAT ? Items.WHEAT_SEEDS : Items.WHEAT;
      TestChecks.isFalse(test, 
          handler.getSlot(4).canInsert(new ItemStack(wrong)),
          "Food slot must reject another species' food.");
    }
    chamber.setStack(3, ItemStack.EMPTY);
    TestChecks.isFalse(test, 
        handler.getSlot(4).canInsert(new ItemStack(Items.WHEAT)),
        "Choose a sample before choosing food.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void stackedSoilCraftingConsumesFourBlocksAndSupportsShiftClick(TestContext test) {
    var player = test.createMockCreativeServerPlayerInWorld();
    var handler = player.playerScreenHandler;
    var input = handler.getCraftingInput();
    for (var feedstock : java.util.List.of(Items.COARSE_DIRT, Items.GRAVEL)) {
      player.getInventory().clear();
      input.clear();
      input.setStack(0, new ItemStack(feedstock, 3));
      input.setStack(1, new ItemStack(Content.COMPOST, 3));
      handler.onContentChanged(input);
      TestChecks.isTrue(test, 
          handler.getSlot(0).getStack().isEmpty(), "Three feedstock must not produce four dirt.");
      input.setStack(0, new ItemStack(feedstock, 12));
      handler.onContentChanged(input);
      var output = handler.getSlot(0).takeStack(4);
      handler.getSlot(0).onTakeItem(player, output);
      TestChecks.isTrue(test, 
          output.isOf(Items.DIRT)
              && output.getCount() == 4
              && input.getStack(0).getCount() == 8
              && input.getStack(1).getCount() == 2,
          "Taking four dirt must consume four feedstock and one compost.");
      handler.onSlotClick(0, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, player);
      int dirt = 0;
      for (var stack : java.util.stream.IntStream.range(0, 36).mapToObj(player.getInventory()::getStack).toList())
        if (stack.isOf(Items.DIRT)) dirt += stack.getCount();
      TestChecks.isTrue(test, 
          dirt == 8 && input.isEmpty(),
          "Shift-click must consume the remaining two batches without duplication.");
    }
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void stackedSoilAlsoWorksInATableAndAutomatedCrafter(TestContext test) {
    var world = test.getWorld();
    var player = test.createMockCreativeServerPlayerInWorld();
    var table =
        new net.minecraft.screen.CraftingScreenHandler(
            1,
            player.getInventory(),
            net.minecraft.screen.ScreenHandlerContext.create(
                world, test.getAbsolutePos(BlockPos.ORIGIN)));
    table.getSlot(1).setStack(new ItemStack(Items.GRAVEL, 4));
    table.getSlot(9).setStack(new ItemStack(Content.COMPOST));
    var output = table.getSlot(0).takeStack(4);
    table.getSlot(0).onTakeItem(player, output);
    TestChecks.isTrue(test, 
        output.isOf(Items.DIRT)
            && output.getCount() == 4
            && table.getSlot(1).getStack().isEmpty()
            && table.getSlot(9).getStack().isEmpty(),
        "The compact recipe must also work across distant crafting-table slots.");
    var pos = test.getAbsolutePos(new BlockPos(2, 14, 2));
    world.setBlockState(pos, Blocks.CRAFTER.getDefaultState());
    var crafter = (net.minecraft.block.entity.CrafterBlockEntity) world.getBlockEntity(pos);
    crafter.setStack(0, new ItemStack(Items.COARSE_DIRT, 3));
    crafter.setStack(1, new ItemStack(Content.COMPOST, 2));
    TestChecks.isTrue(test, 
        net.minecraft.block.CrafterBlock.getCraftingRecipe(world, crafter.createRecipeInput())
            .isEmpty(),
        "A crafter must not cache a valid output for insufficient feedstock.");
    crafter.setStack(0, new ItemStack(Items.COARSE_DIRT, 8));
    TestChecks.isTrue(test, 
        net.minecraft.block.CrafterBlock.getCraftingRecipe(world, crafter.createRecipeInput())
            .isPresent(),
        "Adding the fourth block must invalidate the crafter's insufficient-input result.");
    world
        .getBlockState(pos)
        .scheduledTick(world, pos, net.minecraft.util.math.random.Random.create(1));
    TestChecks.isTrue(test, 
        crafter.getStack(0).getCount() == 4 && crafter.getStack(1).getCount() == 1,
        "An automated craft must consume four feedstock, not just one.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void wateredCropsGrowFasterOnlyInBarrenWorlds(TestContext test) {
    var world = test.getWorld();
    boolean original = dev.reterra.world.WorldOptions.barren(world);
    var pos = test.getAbsolutePos(new BlockPos(2, 70, 2));
    var crop = (net.minecraft.block.CropBlock) Blocks.WHEAT;
    try {
      for (boolean barren : new boolean[] {true, false}) {
        dev.reterra.world.WorldOptions.setBarren(world, barren);
        world.setBlockState(
            pos.down(),
            Blocks.FARMLAND.getDefaultState().with(net.minecraft.block.FarmlandBlock.MOISTURE, 7));
        world.setBlockState(pos, crop.withAge(0));
        var random = net.minecraft.util.math.random.Random.create(42);
        for (int i = 0; i < 200 && crop.getAge(world.getBlockState(pos)) == 0; i++)
          world.getBlockState(pos).randomTick(world, pos, random);
        TestChecks.isTrue(test, 
            crop.getAge(world.getBlockState(pos)) == (barren ? 4 : 1),
            "Watered crops should gain four stages in ReTerra and one in ordinary worlds.");
      }
    } finally {
      dev.reterra.world.WorldOptions.setBarren(world, original);
    }
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void farmhousesProvideFoodAndGuaranteedPlantableRoots(TestContext test) {
    var world = test.getWorld();
    var pos = test.getAbsolutePos(new BlockPos(2, 12, 2));
    world.setBlockState(pos, Blocks.CHEST.getDefaultState());
    var chest = (net.minecraft.block.entity.ChestBlockEntity) world.getBlockEntity(pos);
    chest.setLootTable(
        net.minecraft.registry.RegistryKey.of(
            net.minecraft.registry.RegistryKeys.LOOT_TABLE, Content.id("chests/farmhouse")),
        123);
    chest.generateLoot(null);
    for (var item : java.util.List.of(Items.BREAD, Items.CARROT, Items.POTATO)) {
      int count = 0;
      for (int i = 0; i < chest.size(); i++)
        if (chest.getStack(i).isOf(item)) count += chest.getStack(i).getCount();
      TestChecks.isTrue(test, 
          count == 4, "Every farmhouse must have four " + item.getName().getString() + ".");
    }
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void coreWorkResumesWithoutLeavingAlternatingRows(TestContext test) {
    var world = test.getWorld();
    var state = RestorationState.get(world);
    int cx = (test.getAbsolutePos(BlockPos.ORIGIN).getX() >> 4) + 740;
    int cz = (test.getAbsolutePos(BlockPos.ORIGIN).getZ() >> 4) + 740;
    int left = (cx - 1) * 16, top = (cz - 1) * 16;
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++) {
        world.setChunkForced(cx + x, cz + z, true);
        world.getChunk(cx + x, cz + z);
      }
    try {
      for (int x = 16; x < 32; x++)
        for (int z = 16; z < 32; z++)
          world.setBlockState(
              new BlockPos(left + x, 240, top + z), Blocks.COARSE_DIRT.getDefaultState());
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
              world.getBlockState(column.withY(240)).isOf(Blocks.DIRT),
              "A full batch must resume at its next nearby column without leaving skipped"
                  + " stripes.");
      }
    } finally {
      for (int x = -1; x <= 1; x++)
        for (int z = -1; z <= 1; z++) world.setChunkForced(cx + x, cz + z, false);
    }
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void farmhouseGuidanceUsesACompassTargetInsteadOfTextCoordinates(TestContext test) {
    var world = test.getWorld();
    var state = RestorationState.get(world);
    var before = state.firstSite;
    var completed = new java.util.HashSet<>(state.completed);
    var player = test.createMockCreativeServerPlayerInWorld();
    var discoveries = state.discoveries.remove(player.getUuid());
    try {
      state.completed.clear();
      state.firstSite = test.getAbsolutePos(new BlockPos(20, 4, 20));
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
      if (discoveries == null) state.discoveries.remove(player.getUuid());
      else state.discoveries.put(player.getUuid(), discoveries);
    }
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 60)
  *///? }
  public void coresStopSpendingWhenTheirCoverageTargetsAreMet(TestContext test) {
    var w = test.getWorld();
    var state = RestorationState.get(w);
    int cx = (test.getAbsolutePos(BlockPos.ORIGIN).getX() >> 4) + 300,
        cz = (test.getAbsolutePos(BlockPos.ORIGIN).getZ() >> 4) + 300;
    w.setChunkForced(cx, cz, true);
    w.getChunk(cx, cz);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        w.setBlockState(
            new BlockPos(cx * 16 + x, 180, cz * 16 + z), Blocks.COARSE_DIRT.getDefaultState());
    var p = new BlockPos(cx * 16 + 8, 181, cz * 16 + 8);
    w.setBlockState(p, Content.CORE.getDefaultState());
    for (var part : Multiblocks.parts("restoration_core"))
      w.setBlockState(p.add(part.x(), part.y(), part.z()), part.block().getDefaultState());
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
    test.runAtTick(
        25,
        () -> {
          TestChecks.isTrue(test, 
              core.compost == 32
                  && core.fuel == 99
                  && core.water == 99
                  && core.culture == 0
                  && core.getStack(3).isOf(Content.CULTURE),
              "A Core waiting for plants and wildlife must stop terrain spending once its coverage"
                  + " goals are met.");
          state.machines.remove(p.asLong());
          w.setChunkForced(cx, cz, false);
          test.complete();
        });
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void archivesRecoverDifferentSpeciesAndDoNotRerollOpenedChests(TestContext test) {
    var w = test.getWorld();
    var state = RestorationState.get(w);
    var before = new java.util.HashSet<>(state.recoveredSamples);
    state.recoveredSamples.clear();
    java.util.Set<net.minecraft.item.Item> found = new java.util.HashSet<>();
    var table =
        net.minecraft.registry.RegistryKey.of(
            net.minecraft.registry.RegistryKeys.LOOT_TABLE,
            Content.id("chests/biological_archive"));
    for (int i = 0; i < 4; i++) {
      var p = test.getAbsolutePos(new BlockPos(2 + i, 12, 2));
      w.setBlockState(p, Blocks.CHEST.getDefaultState());
      var chest = (net.minecraft.block.entity.ChestBlockEntity) w.getBlockEntity(p);
      chest.setLootTable(table, 12345);
      chest.generateLoot(null);
      int count = 0;
      for (int slot = 0; slot < chest.size(); slot++)
        if (dev.reterra.machine.MachineLogic.sample(chest.getStack(slot))) {
          count += chest.getStack(slot).getCount();
          found.add(chest.getStack(slot).getItem());
        }
      TestChecks.isTrue(test, count == 1, "Each archive must still preserve only one animal sample.");
      chest.generateLoot(null);
      TestChecks.isTrue(test, 
          state.recoveredSamples.size() == i + 1,
          "Opening a chest again must not sequence another specimen.");
    }
    TestChecks.isTrue(test, 
        found.size() == 4,
        "Four identical raw loot rolls must recover all four species before repeating.");
    state.recoveredSamples.clear();
    state.recoveredSamples.addAll(before);
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void scannerPreviewsAreReadOnlyAndNeverRegisterARegion(TestContext test) {
    var w = test.getWorld();
    var state = RestorationState.get(w);
    var p = test.getAbsolutePos(new BlockPos(3, 12, 3));
    var player = test.createMockCreativeServerPlayerInWorld();
    player.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), 0, 0);
    player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Content.SCANNER));
    int records = state.machines.size();
    var handler =
        dev.reterra.screen.MachineScreenHandler.previewHandler(1, player.getInventory(), w, p);
    TestChecks.isTrue(test, 
        handler.canUse(player)
            && handler.value(45) == 1
            && handler.readOnly()
            && handler.value(18) > 0,
        "A scanner must show an actual local survey before a machine exists.");
    TestChecks.isFalse(test, 
        handler.onButtonClick(player, 1), "Preview cannot register or operate a machine.");
    TestChecks.isFalse(test, handler.onButtonClick(player, 10), "Preview has no machine controls tab.");
    TestChecks.isTrue(test, 
        handler.onButtonClick(player, 4) && state.previewBorders.containsKey(player.getUuid()),
        "Preview can display a temporary boundary.");
    TestChecks.isTrue(test, 
        state.machines.size() == records && player.getMainHandStack().isOf(Content.SCANNER),
        "Surveying must not consume the scanner or create persistent machines.");
    state.previewBorders.remove(player.getUuid());
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void firstRegistrationHasAnIntroductoryContractAndCarePersists(TestContext test) {
    var w = test.getWorld();
    var state = RestorationState.get(w);
    var machines = new java.util.LinkedHashMap<>(state.machines);
    var regions = new java.util.LinkedHashMap<>(state.reclaimedRegions);
    var completed = new java.util.HashSet<>(state.completed);
    state.machines.clear();
    state.reclaimedRegions.clear();
    state.completed.clear();
    var p = test.getAbsolutePos(BlockPos.ORIGIN);
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
            state.writeNbt(new NbtCompound(), w.getRegistryManager()), w.getRegistryManager());
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
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 80)
  *///? }
  public void realHabitatCareRewardsThreeDistinctActionsOnly(TestContext test) {
    var w = test.getWorld();
    var state = RestorationState.get(w);
    int cx = (test.getAbsolutePos(BlockPos.ORIGIN).getX() >> 4) + 280,
        cz = (test.getAbsolutePos(BlockPos.ORIGIN).getZ() >> 4) + 280;
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) {
        w.setChunkForced(cx + dx, cz + dz, true);
        w.getChunk(cx + dx, cz + dz);
      }
    var p = new BlockPos(cx * 16 + 8, 181, cz * 16 + 8);
    w.setBlockState(p, Content.CORE.getDefaultState());
    for (var part : Multiblocks.parts("restoration_core"))
      w.setBlockState(p.add(part.x(), part.y(), part.z()), part.block().getDefaultState());
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
    test.runAtTick(
        20,
        () -> {
          var cow = TestAccess.create(net.minecraft.entity.EntityType.COW, w);
          var sheep = TestAccess.create(net.minecraft.entity.EntityType.SHEEP, w);
          cow.refreshPositionAndAngles(p.getX() + 5, p.getY(), p.getZ() + 5, 0, 0);
          sheep.refreshPositionAndAngles(p.getX() - 5, p.getY(), p.getZ() - 5, 0, 0);
          w.spawnEntity(cow);
          w.spawnEntity(sheep);
          core.paused = false;
          core.stableSeconds = 10;
          var player = test.createMockCreativeServerPlayerInWorld();
          player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
          player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Items.WHEAT, 4));
          cow.interactMob(player, net.minecraft.util.Hand.MAIN_HAND);
          TestChecks.isTrue(test, 
              core.stableSeconds == 25 && cow.isInLove(),
              "Actually feeding wildlife during a healthy trial should earn 15 seconds: stable="
                  + core.stableSeconds + ", love=" + cow.isInLove()
                  + ", species=" + dev.reterra.machine.RestorationEngine.animalSpecies(w, core)
                  + ", loaded=" + dev.reterra.machine.RestorationEngine.allSectorsLoaded(w, core)
                  + ", assembly=" + Multiblocks.issue(w, core.pos, core.kind));
          var grass = p.east(6).down();
          w.setBlockState(grass, Blocks.GRASS_BLOCK.getDefaultState());
          w.setBlockState(grass.up(), Blocks.AIR.getDefaultState());
          player.setStackInHand(
              net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Content.COMPOST, 2));
          var hit =
              new net.minecraft.util.hit.BlockHitResult(
                  net.minecraft.util.math.Vec3d.ofCenter(grass),
                  net.minecraft.util.math.Direction.UP,
                  grass,
                  false);
          dev.reterra.machine.MachineInteraction.use(
              player, w, net.minecraft.util.Hand.MAIN_HAND, hit);
          var crop = p.west(6);
          w.setBlockState(crop.down(), Blocks.FARMLAND.getDefaultState());
          w.setBlockState(crop, Blocks.WHEAT.getDefaultState());
          var cropHit =
              new net.minecraft.util.hit.BlockHitResult(
                  net.minecraft.util.math.Vec3d.ofCenter(crop),
                  net.minecraft.util.math.Direction.UP,
                  crop,
                  false);
          dev.reterra.machine.MachineInteraction.use(
              player, w, net.minecraft.util.Hand.MAIN_HAND, cropHit);
          TestChecks.isTrue(test, 
              core.stableSeconds == 55
                  && core.careTasks.size() == 3
                  && w.getBlockState(crop).get(net.minecraft.state.property.Properties.AGE_7) == 2,
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
          test.complete();
        });
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void surveyCompassPointsToConfirmedSitesWithoutLosingTheCompass(TestContext test) {
    var player = test.createMockCreativeServerPlayerInWorld();
    player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Content.FIELD_JOURNAL));
    player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, new ItemStack(Items.COMPASS));
    var target = test.getAbsolutePos(new BlockPos(3, 12, 3));
    TestChecks.isTrue(test, 
        dev.reterra.world.SurveyClues.point(
            player, dev.reterra.world.ResearchSites.Kind.GREENHOUSE, target),
        "A traced site should update the held compass.");
    var compass = player.getOffHandStack();
    var tracker = compass.get(net.minecraft.component.DataComponentTypes.LODESTONE_TRACKER);
    TestChecks.isTrue(test, 
        compass.isOf(Items.COMPASS)
            && tracker.target().orElseThrow().pos().equals(target)
            && !tracker.tracked(),
        "The compass should navigate to the ruin without requiring a surviving lodestone.");
    var tag = new NbtCompound();
    tag.putString("reterra_clue", "seed_vault");
    player
        .getMainHandStack()
        .set(
            net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
            net.minecraft.component.type.NbtComponent.of(tag));
    TestChecks.isTrue(test, 
        dev.reterra.world.SurveyClues.destination(player.getMainHandStack(), 0)
            == dev.reterra.world.ResearchSites.Kind.SEED_VAULT,
        "Historical notes must retain their own destination rather than always following the"
            + " current chapter.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void firstFarmhouseIsRecordedAndNeverOverwritesPlayerBuilds(TestContext test) {
    var w = test.getWorld();
    var state = RestorationState.get(w);
    BlockPos previous = state.firstSite;
    boolean pending = state.firstSitePending;
    int cx = (test.getAbsolutePos(BlockPos.ORIGIN).getX() >> 4) + 240;
    int cz = (test.getAbsolutePos(BlockPos.ORIGIN).getZ() >> 4) + 240;
    w.setChunkForced(cx, cz, true);
    w.getChunk(cx, cz);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        w.setBlockState(
            new BlockPos(cx * 16 + x, 180, cz * 16 + z), Blocks.COARSE_DIRT.getDefaultState());
        for (int y = 181; y < 192; y++)
          w.setBlockState(new BlockPos(cx * 16 + x, y, cz * 16 + z), Blocks.AIR.getDefaultState());
      }
    BlockPos site = new BlockPos(cx * 16 + 8, 181, cz * 16 + 8);
    BlockPos spawn = site.add(32, 0, 32);
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
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void riverRecoveryRefillsOnlyExposedUnprotectedRiverbeds(TestContext test) {
    var w = test.getWorld();
    int cx = (test.getAbsolutePos(BlockPos.ORIGIN).getX() >> 4) + 220;
    int cz = (test.getAbsolutePos(BlockPos.ORIGIN).getZ() >> 4) + 220;
    w.setChunkForced(cx, cz, true);
    w.getChunk(cx, cz);
    BlockPos bed = new BlockPos(cx * 16 + 4, w.getSeaLevel() - 5, cz * 16 + 4);
    var river =
        w.getRegistryManager()
            //? if <1.21.2 {
            .get(net.minecraft.registry.RegistryKeys.BIOME)
            //? } else {
            /*.getOrThrow(net.minecraft.registry.RegistryKeys.BIOME)
            *///? }
            .getEntry(net.minecraft.util.Identifier.ofVanilla("river"))
            .orElseThrow();
    net.minecraft.server.command.FillBiomeCommand.fillBiome(
        w, new BlockPos(cx * 16, 48, cz * 16), new BlockPos(cx * 16 + 15, 80, cz * 16 + 15), river);
    for (int dy = 1; dy <= 40; dy++) w.setBlockState(bed.up(dy), Blocks.AIR.getDefaultState());
    w.setBlockState(bed.down(), Blocks.STONE.getDefaultState());
    w.setBlockState(bed, Blocks.GRAVEL.getDefaultState());
    var state = RestorationState.get(w);
    state.protectedBlocks.add(bed.up(2).asLong());
    TestChecks.isFalse(test, 
        dev.reterra.world.RiverRecovery.restoreColumn(w, bed.getX(), bed.getZ()),
        "A protected channel column must never be flooded.");
    state.protectedBlocks.remove(bed.up(2).asLong());
    var pump = new RestorationState.Machine(bed.up(5), "water_restorer");
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
        w.getBlockState(bed.up(4)).isOf(Blocks.WATER) && w.getBlockState(bed.up(5)).isAir(),
        "Historic waterline must be respected.");
    BlockPos cave = bed.east(4);
    w.setBlockState(cave, Blocks.GRAVEL.getDefaultState());
    w.setBlockState(cave.down(), Blocks.AIR.getDefaultState());
    TestChecks.isFalse(test, 
        dev.reterra.world.RiverRecovery.restoreColumn(w, cave.getX(), cave.getZ()),
        "An unsupported cave roof is not a riverbed.");
    var plains =
        w.getRegistryManager()
            //? if <1.21.2 {
            .get(net.minecraft.registry.RegistryKeys.BIOME)
            //? } else {
            /*.getOrThrow(net.minecraft.registry.RegistryKeys.BIOME)
            *///? }
            .getEntry(net.minecraft.util.Identifier.ofVanilla("plains"))
            .orElseThrow();
    net.minecraft.server.command.FillBiomeCommand.fillBiome(
        w,
        new BlockPos(cx * 16, 48, cz * 16),
        new BlockPos(cx * 16 + 15, 80, cz * 16 + 15),
        plains);
    w.setBlockState(bed.up(4), Blocks.AIR.getDefaultState());
    TestChecks.isFalse(test, 
        dev.reterra.world.RiverRecovery.restoreColumn(w, bed.getX(), bed.getZ()),
        "Low plains must not be turned into rivers.");
    w.setChunkForced(cx, cz, false);
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void localSurveyCountsWatersideVegetationAndFertileSlopes(TestContext test) {
    var w = test.getWorld();
    BlockPos p = test.getAbsolutePos(new BlockPos(3, 12, 3));
    w.setBlockState(p, Blocks.GRASS_BLOCK.getDefaultState());
    w.setBlockState(p.up(), Blocks.SUGAR_CANE.getDefaultState());
    w.setBlockState(p.east(), Blocks.WATER.getDefaultState());
    w.setBlockState(p.west(), Blocks.AIR.getDefaultState());
    w.setBlockState(p.west().down(), Blocks.STONE.getDefaultState());
    w.setBlockState(p.up(3), Blocks.OAK_LEAVES.getDefaultState());
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
    w.setBlockState(p.up(), Blocks.OAK_LOG.getDefaultState());
    var grown = new RestorationState.Sector();
    dev.reterra.machine.RestorationEngine.sample(w, grown, p);
    TestChecks.isTrue(test, 
        grown.wetland == 1, "Growing a waterside tree must not remove its wetland contribution.");
    var local = dev.reterra.machine.RestorationEngine.localSurvey(w, p);
    TestChecks.isTrue(test, 
        local.sampled() > 0 && local.species() > 0,
        "A scanner survey must work without registering any Core.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void worldOptionDisablesStrippingAndNaturalWildlifeSuppression(TestContext test) {
    var w = test.getWorld();
    boolean before = dev.reterra.world.WorldOptions.barren(w);
    dev.reterra.world.WorldOptions.setBarren(w, false);
    try {
      var p = test.getAbsolutePos(new BlockPos(3, 12, 3));
      w.setBlockState(p, Blocks.GRASS_BLOCK.getDefaultState());
      dev.reterra.world.BarrenWorld.transform(w, w.getChunk(p));
      TestChecks.isTrue(test, 
          w.getBlockState(p).isOf(Blocks.GRASS_BLOCK),
          "Disabling barren generation must preserve new terrain too.");
      var fish = TestAccess.create(net.minecraft.entity.EntityType.COD, w);
      fish.initialize(w, w.getLocalDifficulty(p), net.minecraft.entity.SpawnReason.NATURAL, null);
      TestChecks.isFalse(test, 
          fish.getCommandTags().contains("reterra_barren_spawn"),
          "Ordinary worlds must retain natural wildlife.");
      dev.reterra.world.WorldOptions.setBarren(w, true);
      var barrenFish = TestAccess.create(net.minecraft.entity.EntityType.COD, w);
      barrenFish.initialize(
          w, w.getLocalDifficulty(p), net.minecraft.entity.SpawnReason.NATURAL, null);
      TestChecks.isTrue(test, 
          barrenFish.getCommandTags().contains("reterra_barren_spawn"),
          "Fish must also be suppressed in a dead world.");
    } finally {
      dev.reterra.world.WorldOptions.setBarren(w, before);
    }
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void discoveriesRiverModeAndFinaleSurviveSaving(TestContext test) {
    var state = new RestorationState();
    var p = test.getAbsolutePos(BlockPos.ORIGIN);
    state.firstSite = p;
    state.firstSitePending = true;
    state.heartActivated = true;
    state.heartPos = p.up();
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
            state.writeNbt(new NbtCompound(), test.getWorld().getRegistryManager()),
            test.getWorld().getRegistryManager());
    var machine = copy.machines.get(p.asLong());
    TestChecks.isTrue(test, 
        copy.heartPos.equals(p.up())
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
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void firstEcosystemImprovesLaterRestorationSupplies(TestContext test) {
    var state = RestorationState.get(test.getWorld());
    var before = java.util.EnumSet.noneOf(Ecology.Family.class);
    before.addAll(state.completed);
    state.completed.add(Ecology.Family.PLAINS);
    var m = new RestorationState.Machine(test.getAbsolutePos(BlockPos.ORIGIN), "restoration_core");
    m.setStack(0, new ItemStack(Content.COMPOST));
    m.setStack(1, new ItemStack(Items.COAL));
    m.setStack(3, new ItemStack(Content.CULTURE));
    dev.reterra.machine.MachineLogic.stock(test.getWorld(), m);
    TestChecks.isTrue(test, 
        m.compost == 48 && m.fuel == 160 && m.culture == 0 && m.getStack(3).isOf(Content.CULTURE),
        "Research improves soil and fuel yields; grass cultures remain available for hand"
            + " planting.");
    state.completed.clear();
    state.completed.addAll(before);
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void awakenedHeartLightsUpAndHealsBeyondARegion(TestContext test) {
    var w = test.getWorld();
    var state = RestorationState.get(w);
    var completed = java.util.EnumSet.noneOf(Ecology.Family.class);
    completed.addAll(state.completed);
    boolean active = state.heartActivated;
    BlockPos previousHeart = state.heartPos;
    int previousCursor = state.heartCursor, previousTicks = state.heartTicks;
    BlockPos p = test.getAbsolutePos(new BlockPos(3, 12, 3));
    w.setBlockState(p, Content.HEART.getDefaultState());
    for (var part : Multiblocks.parts("heart_of_the_world"))
      w.setBlockState(p.add(part.x(), part.y(), part.z()), part.block().getDefaultState());
    var m = new RestorationState.Machine(p, "heart_of_the_world");
    int previousAwakening = state.heartAwakening;
    state.heartAwakening = 0;
    var previousCrystal = state.heartCrystal;
    state.machines.put(p.asLong(), m);
    state.completed.clear();
    state.heartActivated = false;
    TestChecks.isFalse(test, dev.reterra.world.HeartAwakening.start(w, m),
        "Normal awakening must still require all four reclaimed ecosystems.");
    TestChecks.isTrue(test, dev.reterra.world.HeartAwakening.startForTesting(w, m)
        && state.heartAwakening == 1 && !state.heartActivated && state.completed.isEmpty(),
        "The testing shortcut starts the real animation without faking ecosystem completion.");
    TestChecks.isFalse(test, dev.reterra.world.HeartAwakening.startForTesting(w, m),
        "The test command must not restart an awakening that is already running.");
    dev.reterra.world.HeartAwakening.disable(w);
    TestChecks.isTrue(test, state.heartAwakening == 0 && !state.heartActivated
        && state.completed.isEmpty(), "Disabling the test must preserve ecosystem progress.");
    state.completed.addAll(java.util.EnumSet.allOf(Ecology.Family.class));
    var player = test.createMockCreativeServerPlayerInWorld();
    dev.reterra.machine.MachineLogic.action(w, m, player, 1);
    TestChecks.isTrue(test, 
        !state.heartActivated && state.heartAwakening == 1,
        "The Heart must finish its animation before activation.");
    for (int i = 0; i < 64; i++) dev.reterra.world.HeartAwakening.tick(w, state);
    TestChecks.isTrue(test, 
        state.heartActivated
            && p.equals(state.heartPos)
            && w.getBlockState(p).get(dev.reterra.machine.MachineBlock.LIT),
        "Awakening must permanently light the Heart.");
    BlockPos ground = p.east(5).down();
    w.setBlockState(ground, Blocks.COARSE_DIRT.getDefaultState());
    w.setBlockState(ground.up(), Blocks.AIR.getDefaultState());
    state.heartCursor = recoveryCursor(p, 8, ground);
    dev.reterra.world.WorldRecovery.tick(w, state);
    TestChecks.isTrue(test, 
        w.getBlockState(ground).isOf(Blocks.DIRT)
            || w.getBlockState(ground).isOf(Blocks.GRASS_BLOCK),
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
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void guideTracksDiscoveriesInsteadOfRestartingAtSeeds(TestContext test) {
    var player = test.createMockCreativeServerPlayerInWorld();
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
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void upgradedReservesAndIncubationProgressPersist(TestContext test) {
    var state = new RestorationState();
    var legacy =
        new RestorationState.Machine(test.getAbsolutePos(BlockPos.ORIGIN), "restoration_core");
    legacy.compost = 256;
    legacy.fuel = 2048;
    legacy.water = 2048;
    legacy.culture = 1024;
    state.machines.put(legacy.pos.asLong(), legacy);
    var chamber = new RestorationState.Machine(legacy.pos.east(), "revival_chamber");
    chamber.pendingSpecies = "bee_sample";
    chamber.revivalTicks = 175;
    chamber.water = 16;
    chamber.setStack(3, new ItemStack(Content.BEE_SAMPLE));
    chamber.setStack(4, new ItemStack(Items.POPPY, 2));
    state.machines.put(chamber.pos.asLong(), chamber);
    var nbt = state.writeNbt(new NbtCompound(), test.getWorld().getRegistryManager());
    dev.reterra.compat.NbtAccess.compound(dev.reterra.compat.NbtAccess.compounds(nbt, "machines"), 0)
        .putInt("resourceVersion", 2);
    var restored = RestorationState.fromNbt(nbt, test.getWorld().getRegistryManager());
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
            && pending.getStack(4).getCount() == 2
            && pending.water == 16,
        "Saving must preserve incubation and its reserved inputs.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 80)
  *///? }
  public void nearbyHomeAnimalsAndShelteredBeesStayInTheCensus(TestContext test) {
    var w = test.getWorld();
    int cx = (test.getAbsolutePos(BlockPos.ORIGIN).getX() >> 4) + 180;
    int cz = (test.getAbsolutePos(BlockPos.ORIGIN).getZ() >> 4) + 180;
    w.setChunkForced(cx, cz, true);
    w.setChunkForced(cx + 2, cz, true);
    w.getChunk(cx, cz);
    w.getChunk(cx + 2, cz);
    test.runAtTick(
        20,
        () -> {
          var core =
              new RestorationState.Machine(
                  new BlockPos(cx * 16 + 8, 180, cz * 16 + 8), "restoration_core");
          var sheep = TestAccess.create(net.minecraft.entity.EntityType.SHEEP, w);
          sheep.refreshPositionAndAngles(cx * 16 + 34, 180, cz * 16 + 8, 0, 0);
          sheep.addCommandTag(dev.reterra.machine.AnimalHabitatGoal.PREFIX + core.pos.asLong());
          TestChecks.isTrue(test, 
              w.spawnEntity(sheep), "Home-animal fixture must spawn into its loaded chunk.");
          TestChecks.isTrue(test, 
              dev.reterra.machine.RestorationEngine.animalCount(w, core) == 1,
              "A home animal just beyond the survey edge must remain counted: "
                  + dev.reterra.machine.RestorationEngine.animalCount(w, core)
                  + ", alive="
                  + sheep.isAlive()
                  + ", baby="
                  + sheep.isBaby()
                  + ", tags="
                  + sheep.getCommandTags());
          TestChecks.isTrue(test, 
              ((dev.reterra.mixin.MobGoalsAccessor) sheep)
                  .reterra$getGoals().getGoals().stream()
                      .anyMatch(
                          goal -> goal.getGoal() instanceof dev.reterra.machine.AnimalHabitatGoal),
              "Loaded home animals need a return-home goal.");
          w.setBlockState(core.pos, Blocks.BEEHIVE.getDefaultState());
          var hive = (net.minecraft.block.entity.BeehiveBlockEntity) w.getBlockEntity(core.pos);
          hive.addBee(net.minecraft.block.entity.BeehiveBlockEntity.BeeData.create(600));
          TestChecks.isTrue(test, 
              dev.reterra.machine.RestorationEngine.animalCount(w, core) == 2
                  && dev.reterra.machine.RestorationEngine.animalSpecies(w, core) == 2,
              "Sheltered bees still count as wildlife.");
          sheep.discard();
          w.setBlockState(core.pos, Blocks.AIR.getDefaultState());
          w.setChunkForced(cx, cz, false);
          w.setChunkForced(cx + 2, cz, false);
          test.complete();
        });
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void everyMachineCanRotateAndTheCoreNeedsItsFurnace(TestContext test) {
    var w = test.getWorld();
    BlockPos p = test.getAbsolutePos(new BlockPos(3, 12, 3));
    for (String kind : dev.reterra.machine.MachineLogic.KINDS) {
      for (int rotation = 0; rotation < 4; rotation++) {
        for (int dx = -3; dx <= 3; dx++)
          for (int dz = -3; dz <= 3; dz++)
            w.setBlockState(p.add(dx, 0, dz), Blocks.AIR.getDefaultState());
        w.setBlockState(
            p, net.minecraft.registry.Registries.BLOCK.get(Content.id(kind)).getDefaultState());
        for (var part : Multiblocks.parts(kind, rotation))
          w.setBlockState(p.add(part.x(), part.y(), part.z()), part.block().getDefaultState());
        TestChecks.isTrue(test, 
            Multiblocks.issue(w, p, kind) == 0,
            "Valid rotation rejected: " + kind + " " + rotation);
        if (kind.equals("restoration_core")) {
          w.setBlockState(p.down(), Blocks.POLISHED_ANDESITE.getDefaultState());
          TestChecks.isTrue(test, Multiblocks.issue(w, p, kind) != 0, "A Core must require its furnace.");
        }
      }
    }
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void waterBucketsReturnInTheirInputSlotAndReservesDoNotBurnWhileIdle(TestContext test) {
    var m = new RestorationState.Machine(test.getAbsolutePos(BlockPos.ORIGIN), "restoration_core");
    m.setStack(0, new ItemStack(Content.COMPOST));
    m.setStack(1, new ItemStack(Items.COAL));
    m.setStack(2, new ItemStack(Items.WATER_BUCKET));
    m.setStack(3, new ItemStack(Content.CULTURE));
    dev.reterra.machine.MachineLogic.stock(m);
    TestChecks.isTrue(test, 
        m.getStack(2).isOf(Items.BUCKET) && m.getStack(7).isEmpty(),
        "Empty bucket must stay in the water slot.");
    int water = m.water, fuel = m.fuel, compost = m.compost;
    for (int i = 0; i < 20; i++) dev.reterra.machine.MachineLogic.stock(m);
    TestChecks.isTrue(test, 
        m.water == water && m.fuel == fuel && m.compost == compost,
        "Waiting must preserve stored supplies.");
    TestChecks.isTrue(test, 
        water < 256 && fuel < 256 && compost < 256, "One item must not restore an entire region.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void pumpSuppliesNearbyMachinesDirectlyFromAPond(TestContext test) {
    var w = test.getWorld();
    var state = RestorationState.get(w);
    BlockPos p = test.getAbsolutePos(new BlockPos(3, 12, 3));
    var pump = new RestorationState.Machine(p, "water_restorer");
    var core = new RestorationState.Machine(p.east(6), "restoration_core");
    pump.fuel = 128;
    pump.compost = 8;
    w.setBlockState(p.north(), Blocks.WATER.getDefaultState());
    state.machines.put(p.asLong(), pump);
    state.machines.put(core.pos.asLong(), core);
    dev.reterra.machine.MachineLogic.pump(w, pump);
    TestChecks.isTrue(test, 
        core.water == 32 && pump.fuel == 127 && pump.compost == 7,
        "Pump must transfer water while charging for actual pumping.");
    TestChecks.isTrue(test, 
        w.getBlockState(p.north()).isOf(Blocks.WATER) && core.getStack(2).isEmpty(),
        "Pond-fed irrigation needs no bucket hauling.");
    state.machines.remove(p.asLong());
    state.machines.remove(core.pos.asLong());
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void flowersCanBeCultivatedWithoutFindingMoreRuins(TestContext test) {
    var w = test.getWorld();
    BlockPos p = test.getAbsolutePos(new BlockPos(3, 12, 3));
    for (int dx = -2; dx <= 2; dx++)
      for (int dz = -2; dz <= 2; dz++) {
        w.setBlockState(p.add(dx, 0, dz), Blocks.GRASS_BLOCK.getDefaultState());
        w.setBlockState(p.add(dx, 1, dz), Blocks.AIR.getDefaultState());
      }
    var player = test.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
    player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Content.COMPOST));
    var hit =
        new net.minecraft.util.hit.BlockHitResult(
            net.minecraft.util.math.Vec3d.ofCenter(p),
            net.minecraft.util.math.Direction.UP,
            p,
            false);
    dev.reterra.machine.MachineInteraction.use(player, w, net.minecraft.util.Hand.MAIN_HAND, hit);
    int flowers = 0;
    for (int dx = -2; dx <= 2; dx++)
      for (int dz = -2; dz <= 2; dz++)
        if (w.getBlockState(p.add(dx, 1, dz)).isIn(net.minecraft.registry.tag.BlockTags.FLOWERS))
          flowers++;
    TestChecks.isTrue(test, 
        flowers == 5 && player.getMainHandStack().isEmpty(),
        "One compost should grow enough flowers to keep a habitat and supply two bee food.");
    TestChecks.isTrue(test, 
        dev.reterra.machine.MachineLogic.foodMatches(
                new ItemStack(Content.BEE_SAMPLE), new ItemStack(Items.POPPY))
            && dev.reterra.machine.MachineLogic.foodCount(new ItemStack(Content.BEE_SAMPLE)) == 2,
        "Bees accept two flowers, including poppies, rather than demanding one specific flower.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void machinesShowStoredSuppliesAndActivity(TestContext test) {
    var w = test.getWorld();
    BlockPos p = test.getAbsolutePos(new BlockPos(3, 12, 3));
    assemble(w, p);
    var m = new RestorationState.Machine(p, "soil_reclaimer");
    m.setStack(0, new ItemStack(Content.COMPOST));
    dev.reterra.machine.MachineBlock.updateVisual(w, m);
    TestChecks.isTrue(test, 
        w.getBlockState(p).get(dev.reterra.machine.MachineBlock.SUPPLIED)
            && !w.getBlockState(p).get(dev.reterra.machine.MachineBlock.LIT),
        "A stocked but idle machine needs its amber indicator.");
    m.compost = 32;
    m.fuel = 128;
    m.water = 128;
    dev.reterra.machine.MachineBlock.updateVisual(w, m);
    TestChecks.isTrue(test, 
        w.getBlockState(p).get(dev.reterra.machine.MachineBlock.LIT),
        "A working machine needs its lit indicator.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void reclaimedRecoverySpreadsPastItsOriginalBorder(TestContext test) {
    var w = test.getWorld();
    int cx = (test.getAbsolutePos(BlockPos.ORIGIN).getX() >> 4) + 110;
    int cz = (test.getAbsolutePos(BlockPos.ORIGIN).getZ() >> 4) + 110;
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
    w.setBlockState(outside.down(), Blocks.STONE.getDefaultState());
    w.setBlockState(outside, Blocks.GRAVEL.getDefaultState());
    w.setBlockState(outside.up(), Blocks.AIR.getDefaultState());
    var state = new RestorationState();
    m.rewildCursor = recoveryCursor(m.pos, 2, outside);
    dev.reterra.machine.RestorationEngine.rewild(w, state, m);
    TestChecks.isTrue(test, 
        w.getBlockState(outside).isOf(Blocks.DIRT)
            || w.getBlockState(outside).isOf(Blocks.GRASS_BLOCK),
        "Natural soil beyond the original 48 x 48 border must heal.");
    dev.reterra.machine.RestorationEngine.advanceSpread(m, 12000);
    TestChecks.isTrue(test, m.spreadRadius == 4, "Expansion must remain bounded for servers.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void revivalTakesTimeLocksItsInputsAndProducesAPair(TestContext test) {
    var w = test.getWorld();
    int cx = (test.getAbsolutePos(BlockPos.ORIGIN).getX() >> 4) + 130;
    int cz = (test.getAbsolutePos(BlockPos.ORIGIN).getZ() >> 4) + 130;
    w.getChunk(cx, cz);
    BlockPos base = new BlockPos(cx * 16, 180, cz * 16);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        w.setBlockState(base.add(x, 0, z), Blocks.GRASS_BLOCK.getDefaultState());
        for (int y = 1; y < 6; y++)
          w.setBlockState(base.add(x, y, z), Blocks.AIR.getDefaultState());
      }
    var core = new RestorationState.Machine(base.add(4, 1, 4), "restoration_core");
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
    var chamber = new RestorationState.Machine(base.add(10, 1, 10), "revival_chamber");
    w.setBlockState(chamber.pos, Content.CHAMBER.getDefaultState());
    for (var part : Multiblocks.parts(chamber.kind))
      w.setBlockState(
          chamber.pos.add(part.x(), part.y(), part.z()), part.block().getDefaultState());
    chamber.setStack(3, new ItemStack(Content.CHICKEN_SAMPLE));
    chamber.setStack(4, new ItemStack(Items.WHEAT_SEEDS, 4));
    chamber.setStack(5, new ItemStack(Content.NUTRIENTS));
    chamber.water = 16;
    var state = RestorationState.get(w);
    state.machines.put(core.pos.asLong(), core);
    state.machines.put(chamber.pos.asLong(), chamber);
    var player = test.createMockCreativeServerPlayerInWorld();
    player.refreshPositionAndAngles(
        chamber.pos.getX(), chamber.pos.getY(), chamber.pos.getZ() + 1, 0, 0);
    dev.reterra.machine.MachineLogic.action(w, chamber, player, 1);
    TestChecks.isTrue(test, 
        !chamber.pendingSpecies.isEmpty()
            && dev.reterra.machine.RestorationEngine.animalCount(w, core) == 0,
        "Revival must start without instant animals.");
    dev.reterra.machine.MachineLogic.action(w, chamber, player, 3);
    TestChecks.isTrue(test, 
        chamber.pendingSpecies.isEmpty()
            && chamber.getStack(3).getCount() == 1
            && chamber.getStack(4).getCount() == 4
            && chamber.water == 16,
        "Cancellation must preserve all unconsumed inputs.");
    dev.reterra.machine.MachineLogic.action(w, chamber, player, 1);
    var handler =
        new dev.reterra.screen.MachineScreenHandler(2, player.getInventory(), w, chamber, false);
    TestChecks.isFalse(test, 
        handler.slots.get(3).canTakeItems(player), "Incubating samples must be locked.");
    for (int i = 0; i < 79; i++) dev.reterra.machine.MachineLogic.revivalTick(w, chamber);
    TestChecks.isTrue(test, 
        dev.reterra.machine.RestorationEngine.animalCount(w, core) == 0,
        "Incubation must finish before birth.");
    dev.reterra.machine.MachineLogic.revivalTick(w, chamber);
    TestChecks.isTrue(test, 
        chamber.pendingSpecies.isEmpty()
            && chamber.getStack(3).isEmpty()
            && dev.reterra.machine.RestorationEngine.animalCount(w, core) == 2,
        "A finished cycle must produce exactly two adults and consume its sample once.");
    TestChecks.isTrue(test, 
        chamber.water == 0 && chamber.getStack(4).isEmpty() && chamber.getStack(5).isEmpty(),
        "Successful revival consumes its reserved inputs.");
    state.machines.remove(core.pos.asLong());
    state.machines.remove(chamber.pos.asLong());
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 460)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 460)
  *///? }
  public void allTwentyCropsCountAcrossBiomesWithinOneFastSurvey(TestContext test) {
    var w = test.getWorld();
    int cx = (test.getAbsolutePos(BlockPos.ORIGIN).getX() >> 4) + 150;
    int cz = (test.getAbsolutePos(BlockPos.ORIGIN).getZ() >> 4) + 150;
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) {
        w.setChunkForced(cx + dx, cz + dz, true);
        w.getChunk(cx + dx, cz + dz);
      }
    BlockPos base = new BlockPos(cx * 16, 180, cz * 16);
    for (int x = 1; x <= 10; x++)
      for (int z = 1; z <= 2; z++) {
        w.setBlockState(
            base.add(x, 0, z),
            Blocks.FARMLAND.getDefaultState().with(net.minecraft.block.FarmlandBlock.MOISTURE, 7));
        w.setBlockState(base.add(x, 1, z), Blocks.WHEAT.getDefaultState());
      }
    var core = new RestorationState.Machine(base.add(8, 1, 8), "restoration_core");
    core.registered = true;
    core.paused = true;
    core.biome = "minecraft:desert";
    w.setBlockState(core.pos, Content.CORE.getDefaultState());
    var state = RestorationState.get(w);
    state.machines.put(core.pos.asLong(), core);
    test.runAtTick(
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
          test.complete();
        });
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 200)
  *///? }
  public void researchSitesBuildWithoutSignsWithUnattachedGenerationBlockEntities(
      TestContext test) {
    ServerWorld world = test.getWorld();
    var pending = new java.util.HashMap<BlockPos, net.minecraft.block.entity.BlockEntity>();
    int[] unattachedReads = {0};
    // Chunk generation exposes block entities before they have a World. A ServerWorld-only
    // fixture missed the SignBlockEntity.setText crash, which sends updates through that World.
    var generation =
        (net.minecraft.world.StructureWorldAccess)
            java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {net.minecraft.world.StructureWorldAccess.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("setBlockState")) {
                    var state = (net.minecraft.block.BlockState) args[1];
                    TestChecks.isFalse(test, 
                        state.getBlock() instanceof net.minecraft.block.AbstractSignBlock,
                        "ReTerra structures must not place signs.");
                    pending.remove((BlockPos) args[0]);
                  }
                  if (method.getName().equals("getBlockEntity")) {
                    BlockPos pos = ((BlockPos) args[0]).toImmutable();
                    if (pending.containsKey(pos)) return pending.get(pos);
                    var live = world.getBlockEntity(pos);
                    if (live == null) return null;
                    var unattached =
                        net.minecraft.block.entity.BlockEntity.createFromNbt(
                            pos,
                            world.getBlockState(pos),
                            live.createNbtWithIdentifyingData(world.getRegistryManager()),
                            world.getRegistryManager());
                    TestChecks.isTrue(test, 
                        unattached != null && unattached.getWorld() == null,
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
    int cx = (test.getAbsolutePos(BlockPos.ORIGIN).getX() >> 4) + 60;
    int cz = (test.getAbsolutePos(BlockPos.ORIGIN).getZ() >> 4) + 60;
    for (var kind : dev.reterra.world.ResearchSites.Kind.values()) {
      BlockPos surface = new BlockPos((cx + kind.ordinal()) * 16 + 8, 90, cz * 16 + 8);
      world.getChunk(surface);
      dev.reterra.world.ResearchSites.build(generation, surface, kind);
      BlockPos chest = dev.reterra.world.ResearchSites.chestPosition(surface, kind);
      var entity = generation.getBlockEntity(chest);
      TestChecks.isTrue(test, 
          entity instanceof net.minecraft.block.entity.LootableContainerBlockEntity,
          "Every site must retain its loot chest during generation: " + kind);
      var loot = (net.minecraft.block.entity.LootableContainerBlockEntity) entity;
      TestChecks.isTrue(test, 
          loot.getWorld() == null
              && loot.getLootTable() != null
              && loot.getLootTable()
                  .getValue()
                  .equals(Content.id("chests/" + kind.name().toLowerCase(java.util.Locale.ROOT))),
          "Loot must be assigned safely before a generated chest is attached: " + kind);
    }
    TestChecks.isTrue(test, 
        unattachedReads[0] >= 6,
        "All six ruins must exercise the world-generation block-entity lifecycle.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 60)
  *///? }
  public void reclaimerConsumesOnlyAfterProducingSoil(TestContext test) {
    ServerWorld w = test.getWorld();
    BlockPos p = test.getAbsolutePos(new BlockPos(3, 2, 3));
    assemble(w, p);
    RestorationState state = RestorationState.get(w);
    RestorationState.Machine m = new RestorationState.Machine(p, "soil_reclaimer");
    m.compost = 256;
    m.fuel = 2048;
    m.water = 2048;
    state.machines.put(p.asLong(), m);
    Inventory inv = (Inventory) w.getBlockEntity(p.north());
    inv.setStack(0, new ItemStack(Items.GRAVEL));
    test.runAtTick(
        25,
        () -> {
          TestChecks.isTrue(test, 
              m.compost == 255 && m.fuel == 2047 && m.water == 2047,
              "One block of work uses one credit, not one whole compost or coal.");
          int dirt = m.getStack(6).isOf(Items.DIRT) ? m.getStack(6).getCount() : 0;
          for (int i = 0; i < inv.size(); i++)
            if (inv.getStack(i).isOf(Items.DIRT)) dirt += inv.getStack(i).getCount();
          TestChecks.isTrue(test, dirt == 1, "One gravel produces one dirt; material is conserved.");
          state.machines.remove(p.asLong());
          test.complete();
        });
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 60)
  *///? }
  public void blockedOutputDoesNotConsumeResources(TestContext test) {
    ServerWorld w = test.getWorld();
    BlockPos p = test.getAbsolutePos(new BlockPos(3, 2, 3));
    assemble(w, p);
    RestorationState state = RestorationState.get(w);
    RestorationState.Machine m = new RestorationState.Machine(p, "soil_reclaimer");
    m.compost = 2;
    m.fuel = 2;
    m.water = 2;
    state.machines.put(p.asLong(), m);
    Inventory inv = (Inventory) w.getBlockEntity(p.north());
    for (int i = 0; i < inv.size(); i++) inv.setStack(i, new ItemStack(Items.COBBLESTONE, 64));
    inv.setStack(0, new ItemStack(Items.GRAVEL, 64));
    m.setStack(6, new ItemStack(Items.DIRT, 64));
    test.runAtTick(
        25,
        () -> {
          TestChecks.isTrue(test, 
              m.compost == 2 && m.fuel == 2 && m.water == 2,
              "A full output must preserve supplies.");
          TestChecks.isTrue(test, 
              inv.getStack(0).getCount() == 64, "A full output must preserve input gravel.");
          state.machines.remove(p.asLong());
          test.complete();
        });
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void persistenceIncludesMachinesScarsAndProgress(TestContext test) {
    ServerWorld w = test.getWorld();
    RestorationState original = new RestorationState();
    BlockPos p = test.getAbsolutePos(new BlockPos(1, 2, 1));
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
    original.protectedBlocks.add(p.up().asLong());
    NbtCompound data = original.writeNbt(new NbtCompound(), w.getRegistryManager());
    RestorationState copy = RestorationState.fromNbt(data, w.getRegistryManager());
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
        copy.protectedBlocks.contains(p.up().asLong()), "Player block protection survives saving.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void dataRecipesAndLootAreLoaded(TestContext test) {
    var manager = test.getWorld().getRecipeManager();
    TestChecks.isTrue(test, 
        TestAccess.recipe(test.getWorld(), "recovery_bundle").isEmpty(),
        "Seed pouches must not have a crafting shortcut.");
    TestChecks.isTrue(test, 
        TestAccess.recipe(test.getWorld(), "restoration_core").isPresent(), "Core recipe must load.");
    var core = TestAccess.recipe(test.getWorld(), "restoration_core").orElseThrow().value();
    //? if <1.21.2 {
    TestChecks.isTrue(test, 
        core.getIngredients().stream().anyMatch(i -> i.test(new ItemStack(Items.REDSTONE_BLOCK)))
            && core.getIngredients().stream()
                .noneMatch(i -> i.test(new ItemStack(Items.LODESTONE))),
        "The Core must use a vanilla redstone block instead of a lodestone.");
    //? } else {
    /*TestChecks.isTrue(test, 
        core.getIngredientPlacement().getIngredients().stream().anyMatch(i -> i.test(new ItemStack(Items.REDSTONE_BLOCK)))
            && core.getIngredientPlacement().getIngredients().stream()
                .noneMatch(i -> i.test(new ItemStack(Items.LODESTONE))),
        "The Core must use a vanilla redstone block instead of a lodestone.");
    *///? }
    for (String shortcut : java.util.List.of("restored_moss", "lodestone"))
      TestChecks.isTrue(test, 
          TestAccess.recipe(test.getWorld(), shortcut).isEmpty(),
          "Vanilla materials must not need the removed " + shortcut + " recipe.");
    TestChecks.isTrue(test, 
        TestAccess.recipe(test.getWorld(), "bee_sample").isEmpty(),
        "Biological samples must come from preserved specimens, not stones and food.");
    TestChecks.isTrue(test, 
        TestAccess.recipe(test.getWorld(), "starter_beehive").isEmpty(),
        "Empty starter hives are salvaged from greenhouses.");
    TestChecks.isTrue(test, 
        test.getWorld().getServer().getAdvancementLoader().get(Content.id("new_beginning")) != null,
        "Endgame advancement must load.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 60)
  *///? }
  public void terrainModePreservesPlacedBlocksAndScars(TestContext test) {
    ServerWorld w = test.getWorld();
    // GameTest encloses templates in a roof; restoration deliberately targets outdoor surfaces.
    BlockPos p = test.getAbsolutePos(new BlockPos(3, 12, 3));
    for (int x = 0; x < 8; x++)
      for (int z = 0; z < 8; z++)
        w.setBlockState(
            test.getAbsolutePos(new BlockPos(x, 11, z)), Blocks.COARSE_DIRT.getDefaultState());
    assemble(w, p);
    RestorationState state = RestorationState.get(w);
    BlockPos protectedSoil = p.east().down();
    state.protectedBlocks.add(protectedSoil.asLong());
    BlockPos natural = null;
    for (int dx = 2; dx <= 4; dx++)
      if (!Ecology.isScar(p.getX() + dx, p.getZ())) {
        natural = p.east(dx).down();
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
    test.runAtTick(
        25,
        () -> {
          TestChecks.isTrue(test, 
              w.getBlockState(protectedSoil).isOf(Blocks.COARSE_DIRT),
              "Machine must not replace a player-placed soil block.");
          TestChecks.isTrue(test, 
              w.getBlockState(target).isOf(Blocks.DIRT),
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
          test.complete();
        });
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void barrenSpawnsDoNotBlockBreedingOrRevival(TestContext test) {
    ServerWorld w = test.getWorld();
    var natural = TestAccess.create(net.minecraft.entity.EntityType.SHEEP, w);
    natural.initialize(
        w,
        w.getLocalDifficulty(test.getAbsolutePos(BlockPos.ORIGIN)),
        net.minecraft.entity.SpawnReason.NATURAL,
        null);
    TestChecks.isTrue(test, 
        natural.getCommandTags().contains("reterra_barren_spawn"),
        "Naturally spawned wildlife is suppressed.");
    var revived = TestAccess.create(net.minecraft.entity.EntityType.SHEEP, w);
    revived.initialize(
        w,
        w.getLocalDifficulty(test.getAbsolutePos(BlockPos.ORIGIN)),
        net.minecraft.entity.SpawnReason.BREEDING,
        null);
    TestChecks.isFalse(test, 
        revived.getCommandTags().contains("reterra_barren_spawn"), "Breeding must stay available.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void reclaimedBiomesPersistAndPreserveOtherBiomeIdentities(TestContext test) {
    ServerWorld w = test.getWorld();
    BlockPos p = test.getAbsolutePos(new BlockPos(3, 2, 3));
    var m = new RestorationState.Machine(p, "restoration_core");
    m.reclaimed = true;
    m.biomeCursor = 4;
    m.biome = w.getBiome(p).getKey().orElseThrow().getValue().toString();
    int x = (p.getX() >> 4) * 16 + 8, z = (p.getZ() >> 4) * 16 + 8;
    var forest =
        w.getRegistryManager()
            //? if <1.21.2 {
            .get(net.minecraft.registry.RegistryKeys.BIOME)
            //? } else {
            /*.getOrThrow(net.minecraft.registry.RegistryKeys.BIOME)
            *///? }
            .getEntry(net.minecraft.util.Identifier.ofVanilla("forest"))
            .orElseThrow();
    BlockPos forestColumn = new BlockPos((x >> 4) * 16, 64, (z >> 4) * 16);
    net.minecraft.server.command.FillBiomeCommand.fillBiome(w, forestColumn, forestColumn, forest);
    dev.reterra.world.ReclaimedBiomes.tick(w, m);
    TestChecks.isTrue(test, 
        m.sectors[4].biomeTransitioned, "A loaded sector must finish its biome transition.");
    var biome = w.getChunk(x >> 4, z >> 4).getBiomeForNoiseGen(x >> 2, 64 >> 2, z >> 2);
    TestChecks.isTrue(test, 
        biome.getKey().orElseThrow().getValue().toString().equals("reterra:reclaimed_plains"),
        "Completed regions must use a distinct reclaimed biome.");
    TestChecks.isTrue(test, 
        w.getChunk(x >> 4, z >> 4)
            .getBiomeForNoiseGen(forestColumn.getX() >> 2, 16, forestColumn.getZ() >> 2)
            .matchesId(net.minecraft.util.Identifier.ofVanilla("forest")),
        "A neighboring biome identity inside the same chunk must remain untouched.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void generationLeavesDeadWoodAndRemovesLivingSurface(TestContext test) {
    ServerWorld w = test.getWorld();
    BlockPos anchor = test.getAbsolutePos(new BlockPos(4, 2, 4));
    // The trunk and its leaves must stay inside the chunk this test transforms.
    BlockPos p = (anchor.getX() & 15) == 15 ? anchor.west() : anchor;
    w.setBlockState(p, Blocks.GRASS_BLOCK.getDefaultState());
    w.setBlockState(p.up(), Blocks.DANDELION.getDefaultState());
    w.setBlockState(p.east(), Blocks.OAK_LOG.getDefaultState());
    w.setBlockState(p.east().up(), Blocks.OAK_LEAVES.getDefaultState());
    dev.reterra.world.BarrenWorld.transform(w, w.getChunk(p));
    TestChecks.isFalse(test, 
        w.getBlockState(p).isOf(Blocks.GRASS_BLOCK), "Generated surface must be barren.");
    TestChecks.isTrue(test, 
        w.getBlockState(p.up()).isAir() && w.getBlockState(p.east().up()).isAir(),
        "Generated flowers and leaves must be removed.");
    TestChecks.isTrue(test, 
        w.getBlockState(p.east()).isOf(Blocks.OAK_LOG),
        "Dead trees must still provide normal wood.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void realBlockPlacementIsProtected(TestContext test) {
    ServerWorld w = test.getWorld();
    BlockPos base = test.getAbsolutePos(new BlockPos(3, 2, 3));
    w.setBlockState(base, Blocks.STONE.getDefaultState());
    w.setBlockState(base.up(), Blocks.AIR.getDefaultState());
    var player = test.createMockCreativeServerPlayerInWorld();
    var hit =
        new net.minecraft.util.hit.BlockHitResult(
            net.minecraft.util.math.Vec3d.ofCenter(base),
            net.minecraft.util.math.Direction.UP,
            base,
            false);
    var placement =
        new net.minecraft.item.ItemPlacementContext(
            w, player, net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Items.DIRT), hit);
    var result = ((net.minecraft.item.BlockItem) Items.DIRT).place(placement);
    TestChecks.isTrue(test, result.isAccepted(), "Fixture must place a real block item.");
    var state = RestorationState.get(w);
    TestChecks.isTrue(test, 
        state.protectedBlocks.contains(base.up().asLong()),
        "The placement hook must protect the actual destination block.");
    state.protectedBlocks.remove(base.up().asLong());
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 200)
  *///? }
  public void everyResearchSiteBuildsWithUsableLoot(TestContext test) {
    ServerWorld w = test.getWorld();
    int cx = (test.getAbsolutePos(BlockPos.ORIGIN).getX() >> 4) + 16;
    int cz = (test.getAbsolutePos(BlockPos.ORIGIN).getZ() >> 4) + 16;
    for (var kind : dev.reterra.world.ResearchSites.Kind.values()) {
      int x = (cx + kind.ordinal()) * 16 + 8, z = cz * 16 + 8;
      w.getChunk(x >> 4, z >> 4);
      BlockPos surface = new BlockPos(x, 80, z);
      dev.reterra.world.ResearchSites.build(w, surface, kind);
      BlockPos chest = dev.reterra.world.ResearchSites.chestPosition(surface, kind);
      TestChecks.isTrue(test, 
          w.getBlockEntity(chest) instanceof net.minecraft.block.entity.ChestBlockEntity,
          "Site must contain a chest: " + kind);
      var inventory = (net.minecraft.inventory.Inventory) w.getBlockEntity(chest);
      boolean supplies = false;
      for (int i = 0; i < inventory.size(); i++)
        if (!inventory.getStack(i).isEmpty()) supplies = true;
      TestChecks.isTrue(test, supplies, "Site loot table must generate resources: " + kind);
      int pouches = 0, cultures = 0, samples = 0;
      for (int i = 0; i < inventory.size(); i++) {
        var stack = inventory.getStack(i);
        if (stack.isOf(Content.RECOVERY_BUNDLE)) pouches += stack.getCount();
        if (stack.isOf(Content.CULTURE)) cultures += stack.getCount();
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
      for (int i = 0; i < inventory.size(); i++)
        if (inventory.getStack(i).isOf(Content.FRAGMENT))
          circuits += inventory.getStack(i).getCount();
      TestChecks.isTrue(test, 
          circuits == (kind == dev.reterra.world.ResearchSites.Kind.RESEARCH_STATION ? 1 : 0),
          "Control circuits belong to Research Stations, separate from preserved samples.");
      TestChecks.isFalse(test, 
          w.getBlockState(surface).isOf(Content.CORE)
              || w.getBlockState(surface).isOf(Content.CHAMBER),
          "Ruins must not give away complete machines.");
      if (kind == dev.reterra.world.ResearchSites.Kind.GREENHOUSE)
        TestChecks.isTrue(test, 
            w.getBlockState(surface.add(-4, 0, 3)).isOf(Blocks.BEEHIVE),
            "Greenhouses must supply an empty hive before the first honeycomb exists.");
    }
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void joiningDoesNotGiveAnyItems(TestContext test) {
    var player = test.createMockCreativeServerPlayerInWorld();
    player.getInventory().clear();
    ReTerra.onJoin(player);
    ReTerra.onJoin(player);
    TestChecks.isTrue(test, 
        player.getInventory().isEmpty(), "First join and reconnect must leave inventory empty.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void seedPouchContainsOnlyOneSmallPlantingStart(TestContext test) {
    var player = test.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
    TestChecks.isFalse(test, player.isCreative(), "Seed-pouch fixture must use survival consumption.");
    player.getInventory().clear();
    player.setStackInHand(
        net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Content.RECOVERY_BUNDLE));
    ReTerra.openBundle(player, player.getMainHandStack());
    int seeds = 0, mushrooms = 0, total = 0;
    for (int i = 0; i < player.getInventory().size(); i++) {
      var stack = player.getInventory().getStack(i);
      total += stack.getCount();
      if (stack.isOf(Items.WHEAT_SEEDS)) seeds += stack.getCount();
      if (stack.isOf(Items.BROWN_MUSHROOM)) mushrooms += stack.getCount();
    }
    TestChecks.isTrue(test, 
        seeds == 2 && mushrooms == 1 && total == 3,
        "Pouch contains exactly 2 wheat seeds and 1 mushroom; no gear, saplings or water.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void hoeCannotProduceDirtFromSterileSoil(TestContext test) {
    var w = test.getWorld();
    var pos = test.getAbsolutePos(new BlockPos(3, 2, 3));
    w.setBlockState(pos, Blocks.COARSE_DIRT.getDefaultState());
    w.setBlockState(pos.up(), Blocks.AIR.getDefaultState());
    var player = test.createMockCreativeServerPlayerInWorld();
    player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
    player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Items.WOODEN_HOE));
    var hit =
        new net.minecraft.util.hit.BlockHitResult(
            net.minecraft.util.math.Vec3d.ofCenter(pos),
            net.minecraft.util.math.Direction.UP,
            pos,
            false);
    var context =
        new net.minecraft.item.ItemUsageContext(player, net.minecraft.util.Hand.MAIN_HAND, hit);
    var result = Items.WOODEN_HOE.useOnBlock(context);
    TestChecks.isTrue(test, 
        result == net.minecraft.util.ActionResult.FAIL
            && w.getBlockState(pos).isOf(Blocks.COARSE_DIRT),
        "Hoe must not bypass restoration.");
    TestChecks.isTrue(test, 
        player.getMainHandStack().getDamage() == 0, "Blocked tilling must not damage the hoe.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void generationAlsoSterilizesBuriedDirt(TestContext test) {
    var w = test.getWorld();
    var pos = test.getAbsolutePos(new BlockPos(4, 6, 4));
    w.setBlockState(pos, Blocks.COARSE_DIRT.getDefaultState());
    w.setBlockState(pos.down(), Blocks.DIRT.getDefaultState());
    w.setBlockState(pos.down(3), Blocks.ROOTED_DIRT.getDefaultState());
    dev.reterra.world.BarrenWorld.transform(w, w.getChunk(pos));
    TestChecks.isFalse(test, 
        w.getBlockState(pos.down()).isOf(Blocks.DIRT),
        "There must not be a free fertile second layer.");
    TestChecks.isTrue(test, 
        w.getBlockState(pos.down(3)).isOf(Blocks.COARSE_DIRT)
            || w.getBlockState(pos.down(3)).isOf(Blocks.GRAVEL),
        "Deep rooted dirt must also be sterile.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void machineSlotsPersistAndShiftClickCannotDuplicateOrInsertOutput(TestContext test) {
    var w = test.getWorld();
    var pos = test.getAbsolutePos(new BlockPos(3, 2, 3));
    assemble(w, pos);
    var state = RestorationState.get(w);
    var m = new RestorationState.Machine(pos, "soil_reclaimer");
    m.dirty = state::markDirty;
    state.machines.put(pos.asLong(), m);
    var player = test.createMockCreativeServerPlayerInWorld();
    player.refreshPositionAndAngles(pos.getX() + 1.5, pos.getY(), pos.getZ() + 1.5, 0, 0);
    player.getInventory().clear();
    var handler =
        new dev.reterra.screen.MachineScreenHandler(1, player.getInventory(), w, m, false);
    player.getInventory().setStack(0, new ItemStack(Items.DIRT, 5));
    TestChecks.isTrue(test, 
        handler.quickMove(player, 35).isEmpty(), "Dirt must not be inserted as fuel or output.");
    player.getInventory().setStack(0, new ItemStack(Items.GRAVEL, 5));
    handler.quickMove(player, 35);
    TestChecks.isTrue(test, 
        m.getStack(3).isOf(Items.GRAVEL)
            && m.getStack(3).getCount() == 5
            && player.getInventory().getStack(0).isEmpty(),
        "Shift-click must transfer gravel exactly once.");
    TestChecks.isFalse(test, 
        handler.slots.get(6).canInsert(new ItemStack(Items.DIRT)),
        "Output slot is extraction-only.");
    var saved = state.writeNbt(new NbtCompound(), w.getRegistryManager());
    var restored = RestorationState.fromNbt(saved, w.getRegistryManager());
    TestChecks.isTrue(test, 
        restored.machines.get(pos.asLong()).getStack(3).getCount() == 5,
        "Inserted items must survive saving and reload.");
    m.setStack(6, new ItemStack(Items.DIRT, 4));
    handler.quickMove(player, 6);
    TestChecks.isTrue(test, m.getStack(6).isEmpty(), "Shift-click extracts the output once.");
    int dirt = 0;
    for (int i = 0; i < player.getInventory().size(); i++)
      if (player.getInventory().getStack(i).isOf(Items.DIRT))
        dirt += player.getInventory().getStack(i).getCount();
    TestChecks.isTrue(test, dirt == 4, "No duplicate items after output extraction.");
    boolean before = m.production;
    player.refreshPositionAndAngles(pos.getX() + 100.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
    TestChecks.isFalse(test, 
        handler.onButtonClick(player, 0),
        "A forged button packet must not operate a distant machine.");
    TestChecks.isTrue(test, m.production == before, "Rejected remote control must preserve machine mode.");
    state.machines.remove(pos.asLong());
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void brokenMachineDropsStoredItemsAndRemovesItsRecord(TestContext test) {
    var w = test.getWorld();
    var pos = test.getAbsolutePos(new BlockPos(3, 2, 3));
    assemble(w, pos);
    var state = RestorationState.get(w);
    var m = new RestorationState.Machine(pos, "soil_reclaimer");
    m.setStack(0, new ItemStack(Content.COMPOST, 3));
    state.machines.put(pos.asLong(), m);
    w.setBlockState(pos, Blocks.AIR.getDefaultState());
    TestChecks.isFalse(test, 
        state.machines.containsKey(pos.asLong()),
        "Destroyed machines must not retain live inventories.");
    int count =
        w
            .getEntitiesByClass(
                net.minecraft.entity.ItemEntity.class,
                new net.minecraft.util.math.Box(pos).expand(2),
                item -> item.getStack().isOf(Content.COMPOST))
            .stream()
            .mapToInt(item -> item.getStack().getCount())
            .sum();
    TestChecks.isTrue(test, count == 3, "Stored supplies must be dropped exactly once.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 60)
  *///? }
  public void idleCoreDoesNotBurnCoalOrCompost(TestContext test) {
    var w = test.getWorld();
    var pos = test.getAbsolutePos(new BlockPos(3, 2, 3));
    w.setBlockState(pos, Content.CORE.getDefaultState());
    w.setBlockState(pos.down(), Blocks.FURNACE.getDefaultState());
    for (var direction : net.minecraft.util.math.Direction.Type.HORIZONTAL)
      w.setBlockState(pos.offset(direction), Blocks.POLISHED_ANDESITE.getDefaultState());
    w.setBlockState(pos.north(2), Blocks.WATER_CAULDRON.getDefaultState());
    var state = RestorationState.get(w);
    var m = new RestorationState.Machine(pos, "restoration_core");
    m.registered = false;
    m.biome = "minecraft:the_void";
    m.compost = 256;
    m.fuel = 2048;
    m.water = 2048;
    state.machines.put(pos.asLong(), m);
    test.runAtTick(
        25,
        () -> {
          TestChecks.isTrue(test, 
              m.compost == 256 && m.fuel == 2048 && m.water == 2048,
              "Survey-only ticks must consume no work credits.");
          state.machines.remove(pos.asLong());
          test.complete();
        });
  }

  private static void assemble(ServerWorld w, BlockPos p) {
    w.setBlockState(p, Content.RECLAIMER.getDefaultState());
    w.setBlockState(p.down(), Blocks.FURNACE.getDefaultState());
    w.setBlockState(p.north(), Blocks.BARREL.getDefaultState());
    w.setBlockState(
        p.south(), Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3));
    if (Multiblocks.missing(w, p) != null)
      throw new AssertionError("Valid reclaimer multiblock was rejected.");
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 100)
  *///? }
  public void healthyTrialCannotBeSkippedByUnloadingAndProgressPersists(TestContext test) {
    var m = new RestorationState.Machine(test.getAbsolutePos(BlockPos.ORIGIN), "restoration_core");
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
            state.writeNbt(new NbtCompound(), test.getWorld().getRegistryManager()),
            test.getWorld().getRegistryManager());
    var copy = restored.machines.get(m.pos.asLong());
    TestChecks.isTrue(test, 
        copy.stableSeconds == 73 && copy.rewildCursor == 1900,
        "Trial and passive succession progress must survive saves.");
    test.complete();
  }

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 120)
  *///? }
  public void completedRegionHealsScarsAndRegrowsOnlyUnprotectedTrunks(TestContext test) {
    ServerWorld w = test.getWorld();
    w.setTimeOfDay(6000);
    int cx = (test.getAbsolutePos(BlockPos.ORIGIN).getX() >> 4) + 35;
    int cz = (test.getAbsolutePos(BlockPos.ORIGIN).getZ() >> 4) + 35;
    w.setChunkForced(cx, cz, true);
    w.getChunk(cx, cz);
    BlockPos origin = new BlockPos(cx * 16, 180, cz * 16);
    var m = new RestorationState.Machine(origin.add(8, 1, 8), "restoration_core");
    m.reclaimed = true;
    m.biome = w.getBiome(origin).getKey().orElseThrow().getValue().toString();
    var state = new RestorationState();
    state.reclaimedRegions.put(m.pos.asLong(), m);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        w.setBlockState(origin.add(x, 0, z), Blocks.GRASS_BLOCK.getDefaultState());
        for (int y = 1; y < 9; y++)
          w.setBlockState(origin.add(x, y, z), Blocks.AIR.getDefaultState());
      }
    BlockPos scar = origin.add(2, 0, 2), protectedSoil = origin.add(3, 0, 3);
    w.setBlockState(scar.down(), Blocks.STONE.getDefaultState());
    w.setBlockState(protectedSoil.down(), Blocks.STONE.getDefaultState());
    w.setBlockState(scar, Blocks.GRAVEL.getDefaultState());
    w.setBlockState(protectedSoil, Blocks.GRAVEL.getDefaultState());
    state.protectedBlocks.add(protectedSoil.asLong());
    BlockPos trunk = origin.add(5, 1, 5), protectedTrunk = origin.add(11, 1, 11);
    for (int y = 0; y < 4; y++) {
      w.setBlockState(trunk.up(y), Blocks.OAK_LOG.getDefaultState());
      w.setBlockState(protectedTrunk.up(y), Blocks.OAK_LOG.getDefaultState());
      state.protectedBlocks.add(protectedTrunk.up(y).asLong());
    }
    test.runAtTick(
        30,
        () -> {
          for (int i = 0; i < 350; i++) dev.reterra.machine.RestorationEngine.rewild(w, state, m);
          for (int i = 0; i < 80; i++) dev.reterra.world.TreeRecovery.tick(w, state);
          TestChecks.isTrue(test, 
              w.getBlockState(scar).isOf(Blocks.DIRT)
                  || w.getBlockState(scar).isOf(Blocks.GRASS_BLOCK),
              "A completed region must reclaim sterile scar soil.");
        });
    // Lighting is asynchronous. Give terrain and crown changes real ticks before the grass pass.
    test.runAtTick(
        60,
        () -> {
          for (int i = 0; i < 350; i++) dev.reterra.machine.RestorationEngine.rewild(w, state, m);
          for (int i = 0; i < 80; i++) dev.reterra.world.TreeRecovery.tick(w, state);
          TestChecks.isTrue(test, 
              w.getBlockState(scar).isOf(Blocks.GRASS_BLOCK),
              "Even scar gravel should heal after completion: "
                  + w.getBlockState(scar)
                  + ", loaded="
                  + dev.reterra.machine.RestorationEngine.loaded(w, scar)
                  + ", ground="
                  + dev.reterra.machine.RestorationEngine.ground(w, scar.getX(), scar.getZ())
                  + ", biome="
                  + w.getBiome(scar).getKey()
                  + ", expected="
                  + m.biome
                  + ", light="
                  + w.getLightLevel(scar.up())
                  + ", day="
                  + w.getTimeOfDay());
          TestChecks.isTrue(test, 
              w.getBlockState(protectedSoil).isOf(Blocks.GRAVEL),
              "Player gravel must survive succession.");
          TestChecks.isTrue(test, 
              w.getBlockState(trunk.west().up(3)).isOf(Blocks.OAK_LEAVES),
              "Natural dead trunks should regrow a crown.");
          TestChecks.isTrue(test, 
              w.getBlockState(protectedTrunk.up(4)).isAir(),
              "Player log pillars must not grow leaves.");
          TestChecks.isTrue(test, 
              m.compost == 0 && m.fuel == 0 && m.water == 0,
              "Recovered ecosystems need no machine supplies.");
          w.setChunkForced(cx, cz, false);
          test.complete();
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

  //? if <1.21.5 {
  @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 160)
  //? } else {
  /*@GameTest(structure = EMPTY_STRUCTURE, maxTicks = 160)
  *///? }
  public void locateCommandFindsAnActualArchiveAndSupportsVanillaSyntax(TestContext test) {
    ServerWorld w = test.getWorld();
    var from = test.getAbsolutePos(BlockPos.ORIGIN).add(3000, 0, 3000);
    var kind = dev.reterra.world.ResearchSites.Kind.BIOLOGICAL_ARCHIVE;
    BlockPos candidate =
        dev.reterra.world.SiteLocator.candidates(w.getSeed(), from, kind).getFirst();
    w.getChunk(candidate.getX() >> 4, candidate.getZ() >> 4);
    BlockPos surface =
        candidate.withY(
            w.getTopY(
                    net.minecraft.world.Heightmap.Type.WORLD_SURFACE,
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
        new net.minecraft.server.command.CommandOutput() {
          public void sendMessage(net.minecraft.text.Text text) {
            output.append(text.getString()).append('\n');
          }

          public boolean shouldReceiveFeedback() {
            return true;
          }

          public boolean shouldTrackOutput() {
            return true;
          }

          public boolean shouldBroadcastConsoleToOps() {
            return false;
          }
        };
    var source =
        w.getServer()
            .getCommandSource()
            .withWorld(w)
            .withPosition(net.minecraft.util.math.Vec3d.ofCenter(surface))
            .withOutput(sink);
    var dispatcher = w.getServer().getCommandManager().getDispatcher();
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
    test.runAtTick(
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
          test.complete();
        });
  }
}
