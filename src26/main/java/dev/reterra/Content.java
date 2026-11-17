package dev.reterra;

import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class Content {
  public static final Map<String, Item> ITEMS = new LinkedHashMap<>();
  public static final Item COMPOST = item("organic_compost");
  public static final Item CULTURE = item("grass_culture");
  public static final Item NUTRIENTS = item("nutrient_culture");
  public static final Item FILTER = item("water_filter");
  public static final Item FRAGMENT = item("archive_fragment");
  public static final Item SHEEP_SAMPLE = item("sheep_sample");
  public static final Item COW_SAMPLE = item("cow_sample");
  public static final Item CHICKEN_SAMPLE = item("chicken_sample");
  public static final Item BEE_SAMPLE = item("bee_sample");
  public static final Item FIELD_JOURNAL = item("field_journal");
  public static final Item RECOVERY_BUNDLE = item("recovery_bundle");
  public static final Item SCANNER = item("environmental_scanner");
  public static final Block RECLAIMER = machine("soil_reclaimer", Blocks.FURNACE);
  public static final Block CORE = machine("restoration_core", Blocks.LODESTONE);
  public static final Block WATERWORKS = machine("water_restorer", dev.reterra.compat.GameAccess.COPPER_BLOCK);
  public static final Block CHAMBER = machine("revival_chamber", Blocks.IRON_BLOCK);
  public static final Block HEART = machine("heart_of_the_world", Blocks.ENCHANTING_TABLE);

  public static Identifier id(String path) {
    return Identifier.fromNamespaceAndPath("reterra", path);
  }

  private static Item item(String name) {
    Item result =
        Registry.register(
            BuiltInRegistries.ITEM, id(name), new RestorationItem(name, itemSettings(name, false)));
    ITEMS.put(name, result);
    return result;
  }

  private static Block machine(String name, Block reference) {
    var settings = BlockBehaviour.Properties.ofFullCopy(reference);
    settings.setId(net.minecraft.resources.ResourceKey.create(
        net.minecraft.core.registries.Registries.BLOCK, id(name)));
    Block block =
        Registry.register(
            BuiltInRegistries.BLOCK,
            id(name),
            new dev.reterra.machine.MachineBlock(
                settings
                    .lightLevel(state -> state.getValue(dev.reterra.machine.MachineBlock.LIT) ? 5 : 0)
                    .strength(3.5f)
                    .pushReaction(dev.reterra.compat.GameAccess.IMMOVABLE)
                    .requiresCorrectToolForDrops()));
    ITEMS.put(
        name,
        Registry.register(BuiltInRegistries.ITEM, id(name), new BlockItem(block, itemSettings(name, true))));
    return block;
  }

  private static Item.Properties itemSettings(String name, boolean block) {
    var settings = new Item.Properties();
    settings.setId(net.minecraft.resources.ResourceKey.create(
        net.minecraft.core.registries.Registries.ITEM, id(name)));
    if (block) settings.useBlockDescriptionPrefix();
    return settings;
  }

  public static void initialize() {
    Registry.register(
        BuiltInRegistries.CREATIVE_MODE_TAB,
        id("restoration"),
        FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.reterra"))
            .icon(() -> new ItemStack(CULTURE))
            .displayItems(
                (ctx, entries) ->
                    ITEMS.values().stream()
                        .filter(item -> item != FIELD_JOURNAL)
                        .forEach(entries::accept))
            .build());
  }

  public static boolean isMachine(Block b) {
    return b == RECLAIMER || b == CORE || b == WATERWORKS || b == CHAMBER || b == HEART;
  }
}
