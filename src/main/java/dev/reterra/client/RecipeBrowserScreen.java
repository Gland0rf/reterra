package dev.reterra.client;

import com.google.gson.JsonParser;
import dev.reterra.Content;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Recipes use the same ingredient grids as the crafting data. */
public final class RecipeBrowserScreen extends Screen {
  private record Entry(String id, ItemStack output, List<ItemStack> grid, boolean shaped) {}

  private final Screen parent;
  private final String selection;
  private final List<Entry> entries = new ArrayList<>(), visible = new ArrayList<>();
  private final List<Row> buttons = new ArrayList<>();
  private final FieldUi.Button[] filters = new FieldUi.Button[3];
  private final ArrayDeque<Entry> history = new ArrayDeque<>();
  private Entry selected;
  private TextFieldWidget search;
  private FieldUi.Button previous;
  private ItemStack source = ItemStack.EMPTY;
  private String query = "";
  private int category, first, left, top, panelWidth, panelHeight, rows, listWidth, gridX, gridY;

  public RecipeBrowserScreen(Screen parent, String selection) {
    super(net.minecraft.text.Text.literal("Recipes"));
    this.parent = parent;
    this.selection = selection;
  }

  @Override
  protected void init() {
    if (entries.isEmpty()) load();
    panelWidth = Math.min(440, width - 16);
    panelHeight = Math.min(280, height - 16);
    left = (width - panelWidth) / 2;
    top = (height - panelHeight) / 2;
    listWidth = Math.min(176, panelWidth * 2 / 5);
    gridX = left + listWidth + 28;
    gridY = top + 88;
    rows = Math.max(1, (panelHeight - 116) / 24);
    search =
        addDrawableChild(
            new TextFieldWidget(
                textRenderer, left + 12, top + 36, listWidth, 18, net.minecraft.text.Text.literal("Search recipes")));
    search.setPlaceholder(net.minecraft.text.Text.literal("Search recipes"));
    search.setText(query);
    search.setChangedListener(
        value -> {
          query = value;
          first = 0;
          refresh();
        });
    String[] labels = {
      "All", listWidth < 144 ? "Blocks" : "Machines", listWidth < 144 ? "Items" : "Supplies"
    };
    int firstWidth = Math.max(27, listWidth / 5);
    int[] filterWidths = {
      firstWidth,
      (listWidth - firstWidth - 4) / 2,
      listWidth - firstWidth - 4 - (listWidth - firstWidth - 4) / 2
    };
    int filterX = left + 12;
    for (int i = 0; i < 3; i++) {
      int index = i;
      filters[i] =
          addDrawableChild(
              FieldUi.button(
                  net.minecraft.text.Text.literal(labels[i]),
                  b -> {
                    category = index;
                    first = 0;
                    refresh();
                  },
                  filterX,
                  top + 59,
                  filterWidths[i],
                  16));
      filterX += filterWidths[i] + 2;
    }
    buttons.clear();
    for (int i = 0; i < rows; i++) buttons.add(addDrawableChild(new Row(i)));
    addDrawableChild(
        FieldUi.button(
            net.minecraft.text.Text.literal("Back"), b -> close(), left + panelWidth - 64, top + 10, 52, 18));
    previous =
        addDrawableChild(
            FieldUi.button(
                net.minecraft.text.Text.literal("Previous recipe"),
                b -> {
                  if (!history.isEmpty()) {
                    selected = history.pop();
                    source = ItemStack.EMPTY;
                    category = 0;
                    search.setText("");
                    refresh();
                  }
                },
                gridX,
                top + panelHeight - 30,
                Math.min(104, panelWidth - listWidth - 40),
                18));
    refresh();
    int chosen = visible.indexOf(selected);
    if (chosen >= 0 && (chosen < first || chosen >= first + rows)) {
      first = Math.clamp(chosen, 0, Math.max(0, visible.size() - rows));
      refresh();
    }
  }

  private void load() {
    try (var reader =
        client
            .getResourceManager()
            .getResourceOrThrow(Content.id("field_recipes.json"))
            .getReader()) {
      for (var element : JsonParser.parseReader(reader).getAsJsonArray()) {
        var data = element.getAsJsonObject();
        var result = data.getAsJsonObject("result");
        var grid = new ArrayList<ItemStack>();
        for (var cell : data.getAsJsonArray("grid")) {
          if (cell.isJsonNull()) grid.add(ItemStack.EMPTY);
          else {
            var input = cell.getAsJsonObject();
            grid.add(
                new ItemStack(
                    Registries.ITEM.get(Identifier.of(input.get("item").getAsString())),
                    input.get("count").getAsInt()));
          }
        }
        entries.add(
            new Entry(
                data.get("id").getAsString(),
                new ItemStack(
                    Registries.ITEM.get(Identifier.of(result.get("id").getAsString())),
                    result.get("count").getAsInt()),
                grid,
                data.get("shaped").getAsBoolean()));
      }
    } catch (java.io.IOException | RuntimeException error) {
      org.slf4j.LoggerFactory.getLogger("ReTerra").warn("Could not load the recipe browser", error);
    }
    selected =
        entries.stream()
            .filter(e -> e.id.equals(selection) || e.id.equals("reterra:" + selection))
            .findFirst()
            .orElse(entries.isEmpty() ? null : entries.getFirst());
  }

  private void refresh() {
    visible.clear();
    String needle = query.toLowerCase(Locale.ROOT).trim();
    for (var entry : entries) {
      boolean machine =
          entry.output.getItem() instanceof net.minecraft.item.BlockItem block
              && Content.isMachine(block.getBlock());
      if (category == 1 && !machine || category == 2 && machine) continue;
      String name = entry.output.getName().getString() + " " + variant(entry);
      if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle)) continue;
      visible.add(entry);
    }
    if (!visible.contains(selected)) selected = visible.isEmpty() ? null : visible.getFirst();
    first = Math.clamp(first, 0, Math.max(0, visible.size() - rows));
    for (int i = 0; i < filters.length; i++)
      if (filters[i] != null) filters[i].selected(category == i);
    for (int i = 0; i < buttons.size(); i++) {
      var button = buttons.get(i);
      button.visible = first + i < visible.size();
      if (button.visible) button.setMessage(visible.get(first + i).output.getName());
    }
    if (previous != null) previous.active = !history.isEmpty();
  }

  private String variant(Entry entry) {
    String id = entry.id.substring(entry.id.indexOf(':') + 1);
    if (id.startsWith("compost_from_")) return titleCase(id.substring(13));
    if (id.startsWith("compact_soil_")) return "Stacked " + id.substring(13).replace('_', ' ');
    if (id.startsWith("fertile_soil")) return id.endsWith("coarse") ? "Coarse dirt" : "Gravel";
    return "";
  }

  private String titleCase(String value) {
    return Character.toUpperCase(value.charAt(0)) + value.substring(1).replace('_', ' ');
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
    if (mouseX < left + 8 || mouseX > left + listWidth + 20)
      return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    first = Math.clamp(first - (int) Math.signum(vertical), 0, Math.max(0, visible.size() - rows));
    refresh();
    return true;
  }

  @Override
  public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
    super.renderBackground(c, mouseX, mouseY, delta);
    FieldUi.panel(c, left, top, panelWidth, panelHeight, false);
    c.drawItem(new ItemStack(Content.FIELD_JOURNAL), left + 12, top + 10);
    c.drawText(textRenderer, title, left + 34, top + 14, FieldUi.INK, false);
    c.fill(
        left + listWidth + 22, top + 36, left + listWidth + 23, top + panelHeight - 12, 0xFF9CA48F);
    c.drawText(
        textRenderer,
        visible.size() + (visible.size() == 1 ? " recipe" : " recipes"),
        left + 12,
        top + panelHeight - 23,
        FieldUi.MUTED,
        false);
    if (visible.size() > rows) {
      int track = rows * 24 - 4;
      int knob = Math.max(12, track * rows / visible.size());
      int offset = (track - knob) * first / Math.max(1, visible.size() - rows);
      c.fill(left + listWidth + 15, top + 83, left + listWidth + 18, top + 83 + track, 0xFF9CA48F);
      c.fill(
          left + listWidth + 15,
          top + 83 + offset,
          left + listWidth + 18,
          top + 83 + offset + knob,
          FieldUi.GREEN);
    }
    if (selected == null) {
      FieldUi.wrapped(c, 
          textRenderer,
          net.minecraft.text.Text.literal(
              entries.isEmpty()
                  ? "Recipes are unavailable. Try reloading your resource packs."
                  : "No matching recipes. Try another search."),
          gridX,
          top + 54,
          panelWidth - listWidth - 40,
          FieldUi.INK);
      return;
    }
    int detailWidth = panelWidth - listWidth - 40;
    FieldUi.wrapped(c, 
        textRenderer, selected.output.getName(), gridX, top + 44, detailWidth, FieldUi.INK);
    String variant = variant(selected);
    if (!variant.isEmpty())
      c.drawText(
          textRenderer,
          textRenderer.trimToWidth(variant, detailWidth),
          gridX,
          top + 70,
          FieldUi.MUTED,
          false);
    for (int i = 0; i < 9; i++) {
      int px = gridX + i % 3 * 20, py = gridY + i / 3 * 20;
      FieldUi.slot(c, px, py);
      if (i < selected.grid.size()) drawStack(c, selected.grid.get(i), px, py);
    }
    c.drawText(textRenderer, ">", gridX + 67, gridY + 24, FieldUi.GREEN, false);
    FieldUi.slot(c, gridX + 84, gridY + 20);
    drawStack(c, selected.output, gridX + 84, gridY + 20);
    String crafting =
        selected.id.contains("compact_soil")
            ? "Keep 4 blocks in one slot."
            : selected.shaped || selected.grid.size() > 4 ? "Crafting table" : "Any crafting grid";
    FieldUi.wrapped(c, 
        textRenderer, net.minecraft.text.Text.literal(crafting), gridX, gridY + 67, detailWidth, FieldUi.MUTED);
    int hintY = gridY + 92;
    var hint =
        net.minecraft.text.Text.literal(source.isEmpty() ? "Click an ingredient to learn more." : sourceHint(source));
    for (var line : textRenderer.wrapLines(hint, detailWidth)) {
      if (hintY + 9 > top + panelHeight - 35) break;
      c.drawText(
          textRenderer, line, gridX, hintY, source.isEmpty() ? FieldUi.MUTED : FieldUi.INK, false);
      hintY += 10;
    }
  }

  private String sourceHint(ItemStack item) {
    String id = Registries.ITEM.getId(item.getItem()).getPath();
    if (id.endsWith("_sample")) return "Preserved in biological archives.";
    return switch (id) {
      case "recovery_bundle", "wheat_seeds" -> "Recover seeds from an old farmhouse.";
      case "archive_fragment" -> "Craft with copper, iron, redstone and glass, or find one in a Research Station.";
      case "nutrient_culture" -> "Craft a glass bottle with wheat and bone meal, or find two in a Research Station.";
      case "charcoal" -> "Smelt a log in a furnace.";
      case "glass", "glass_bottle" -> "Smelt sand for glass. Three glass make three bottles.";
      case "iron_ingot", "copper_ingot", "gold_ingot" ->
          "Mine the ore, then smelt it in a furnace.";
      case "diamond" -> "Mine diamond ore deep underground.";
      case "redstone" -> "Mine redstone ore with an iron pickaxe.";
      case "redstone_block" -> "Craft nine redstone dust into one block.";
      case "bone_meal" -> "Craft skeleton bones into bone meal.";
      case "gravel", "coarse_dirt", "sand" -> "Collect it from the ground.";
      case "brown_mushroom" -> "A Seed Pouch preserves one. Plant it in a dark spot to grow more.";
      default -> "Check Minecraft's recipe book for " + item.getName().getString() + ".";
    };
  }

  private void drawStack(DrawContext c, ItemStack stack, int x, int y) {
    if (stack.isEmpty()) return;
    c.drawItem(stack, x, y);
    //? if <1.21.2 {
    c.drawItemInSlot(textRenderer, stack, x, y);
    //? } else {
    /*c.drawStackOverlay(textRenderer, stack, x, y);
    *///? }
  }

  private ItemStack hovered(double mouseX, double mouseY) {
    if (selected == null) return ItemStack.EMPTY;
    for (int i = 0; i < selected.grid.size(); i++) {
      int px = gridX + i % 3 * 20, py = gridY + i / 3 * 20;
      if (mouseX >= px && mouseX < px + 16 && mouseY >= py && mouseY < py + 16)
        return selected.grid.get(i);
    }
    return mouseX >= gridX + 84
            && mouseX < gridX + 100
            && mouseY >= gridY + 20
            && mouseY < gridY + 36
        ? selected.output
        : ItemStack.EMPTY;
  }

  @Override
  public void render(DrawContext c, int mouseX, int mouseY, float delta) {
    super.render(c, mouseX, mouseY, delta);
    var item = hovered(mouseX, mouseY);
    if (!item.isEmpty()) c.drawItemTooltip(textRenderer, item, mouseX, mouseY);
  }

  @Override
  //? if <1.21.9 {
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
  //? } else {
  /*public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
    double mouseX = click.x(), mouseY = click.y();
    int button = click.button();
  *///? }
    var stack = hovered(mouseX, mouseY);
    if (button == 0 && !stack.isEmpty()) {
      var recipe =
          entries.stream().filter(e -> e.output.isOf(stack.getItem()) && e != selected).findFirst();
      if (recipe.isPresent()) {
        if (selected != null) history.push(selected);
        selected = recipe.get();
        source = ItemStack.EMPTY;
        category = 0;
        search.setText("");
        refresh();
        int index = visible.indexOf(selected);
        if (index >= 0) {
          first = Math.clamp(index, 0, Math.max(0, visible.size() - rows));
          refresh();
        }
      } else source = stack.copy();
      return true;
    }
    //? if <1.21.9 {
    return super.mouseClicked(mouseX, mouseY, button);
    //? } else {
    /*return super.mouseClicked(click, doubled);
    *///? }
  }

  @Override
  public void close() {
    client.setScreen(
        parent instanceof MachineScreen machine && !machine.validContext() ? null : parent);
  }

  @Override
  public boolean shouldPause() {
    return false;
  }

  private final class Row extends ButtonWidget {
    private final int row;

    Row(int row) {
      super(
          left + 12,
          top + 83 + row * 24,
          listWidth,
          22,
          net.minecraft.text.Text.empty(),
          button -> {
            selected = RecipeBrowserScreen.this.visible.get(first + row);
            source = ItemStack.EMPTY;
          },
          DEFAULT_NARRATION_SUPPLIER);
      this.row = row;
    }

    //? if >=1.21.11 {
    /*@Override
    protected void drawLabel(net.minecraft.client.font.DrawnTextConsumer consumer) {}
    *///? }

    @Override
    //? if <1.21.11 {
    protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
    //? } else {
    /*protected void drawIcon(DrawContext c, int mouseX, int mouseY, float delta) {
    *///? }
      if (first + row >= RecipeBrowserScreen.this.visible.size()) return;
      var entry = RecipeBrowserScreen.this.visible.get(first + row);
      boolean chosen = entry == selected;
      int x = getX(), y = getY();
      c.fill(
          x,
          y,
          x + getWidth(),
          y + getHeight(),
          isHovered() || isFocused() ? FieldUi.COPPER : chosen ? FieldUi.GREEN : FieldUi.BORDER);
      c.fill(
          x + 1, y + 1, x + getWidth() - 1, y + getHeight() - 1, chosen ? 0xFFC3D0AB : 0xFFD3D5C7);
      c.drawItem(entry.output, x + 3, y + 3);
      String variant = variant(entry);
      c.drawText(
          textRenderer,
          textRenderer.trimToWidth(entry.output.getName().getString(), getWidth() - 27),
          x + 24,
          y + (variant.isEmpty() ? 7 : 3),
          FieldUi.INK,
          false);
      if (!variant.isEmpty())
        c.drawText(
            textRenderer,
            textRenderer.trimToWidth(variant, getWidth() - 27),
            x + 24,
            y + 12,
            FieldUi.MUTED,
            false);
    }
  }
}
