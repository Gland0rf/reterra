package dev.reterra;

import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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
  public static final Block WATERWORKS = machine("water_restorer", Blocks.COPPER_BLOCK);
  public static final Block CHAMBER = machine("revival_chamber", Blocks.IRON_BLOCK);
  public static final Block HEART = machine("heart_of_the_world", Blocks.ENCHANTING_TABLE);

  public static Identifier id(String path) {
    return Identifier.of("reterra", path);
  }

  private static Item item(String name) {
    Item result =
        Registry.register(
            Registries.ITEM, id(name), new RestorationItem(name, itemSettings(name, false)));
    ITEMS.put(name, result);
    return result;
  }

  private static Block machine(String name, Block reference) {
    var settings = AbstractBlock.Settings.copy(reference);
    //? if >=1.21.2 {
    /*settings.registryKey(net.minecraft.registry.RegistryKey.of(
        net.minecraft.registry.RegistryKeys.BLOCK, id(name)));
    *///? }
    Block block =
        Registry.register(
            Registries.BLOCK,
            id(name),
            new dev.reterra.machine.MachineBlock(
                settings
                    .luminance(state -> state.get(dev.reterra.machine.MachineBlock.LIT) ? 5 : 0)
                    .strength(3.5f)
                    .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK)
                    .requiresTool()));
    ITEMS.put(
        name,
        Registry.register(Registries.ITEM, id(name), new BlockItem(block, itemSettings(name, true))));
    return block;
  }

  private static Item.Settings itemSettings(String name, boolean block) {
    var settings = new Item.Settings();
    //? if >=1.21.2 {
    /*settings.registryKey(net.minecraft.registry.RegistryKey.of(
        net.minecraft.registry.RegistryKeys.ITEM, id(name)));
    if (block) settings.useBlockPrefixedTranslationKey();
    *///? }
    return settings;
  }

  public static void initialize() {
    Registry.register(
        Registries.ITEM_GROUP,
        id("restoration"),
        FabricItemGroup.builder()
            .displayName(Text.translatable("itemGroup.reterra"))
            .icon(() -> new ItemStack(CULTURE))
            .entries(
                (ctx, entries) ->
                    ITEMS.values().stream()
                        .filter(item -> item != FIELD_JOURNAL)
                        .forEach(entries::add))
            .build());
  }

  public static boolean isMachine(Block b) {
    return b == RECLAIMER || b == CORE || b == WATERWORKS || b == CHAMBER || b == HEART;
  }
}
