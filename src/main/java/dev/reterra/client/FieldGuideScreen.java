package dev.reterra.client;

import dev.reterra.Content;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Saved next tasks with short, browsable field notes. */
public final class FieldGuideScreen extends Screen {
  private static dev.reterra.screen.GuidePayload progress = initial();
  private static int remembered, lastObjective = -1;
  private int page, left, top, panelWidth, panelHeight, scroll, maxScroll;
  private final FieldUi.Button[] chapters = new FieldUi.Button[6];
  private static final String[] LABELS = {"Seeds", "Farm", "Tools", "Plants", "Animals", "Heart"};
  private static final String[] TITLES = {
    "Find the first seeds", "Grow a farm", "Restore the soil",
    "Build a habitat", "Bring back wildlife", "Reclaim the ecosystems"
  };
  private static final String[][] PAGES = {
    {
      "Follow the farmhouse compass. Search the pantry for food and a Seed Pouch. Use the pouch to"
          + " recover wheat seeds and a mushroom.",
      "Keep some carrots and potatoes to plant.",
      "To follow a route, hold the Field Notes item from a chest in your main hand and a compass"
          + " in your offhand. Right-click the notes to set the compass."
    },
    {
      "A log with a mushroom or rotten flesh makes compost. Mix one compost with four gravel or"
          + " coarse dirt to make dirt.",
      "Till beside water. Plant seeds and spare roots. Use compost on crops to speed growth."
    },
    {
      "Bottle, wheat seeds and compost make grass culture. Use it on dirt. Refill the bottle from"
          + " grass.",
      "Craft a Reclaimer, then a Core. Recipes shows the ingredients; Build shows the supports.",
      "A registered Core restores soil automatically when supplied. You plant the grass, crops"
          + " and trees."
    },
    {
      "Plant crops, grow trees, add flowers and make a pond inside the Core's boundary. Habitat"
          + " shows what is missing.",
      "For more flowers, use compost or bone meal on grass."
    },
    {
      "Craft a Control Circuit from copper, iron, redstone and glass, or find one in a Research"
          + " Station. Recipes shows the pattern.",
      "Seed Vault and Research Station notes lead to Biological Archives for animal samples."
          + " Hold the notes in your main hand, a compass in your offhand, then right-click the notes.",
      "One glass bottle, one wheat and one bone meal make a Nutrient Culture. Research Stations"
          + " also hold two cultures.",
      "Build a Revival Chamber inside a registered region. Add the sample's food, water and"
          + " nutrient culture. A pair emerges after 20 seconds."
    },
    {
      "Keep two animal species and a healthy habitat through the trial. Feeding adults, tending"
          + " crops and planting local vegetation can shorten it.",
      "Reclaim plains, forest, swamp and highlands, then build and awaken the Heart."
    }
  };

  private static dev.reterra.screen.GuidePayload initial() {
    return new dev.reterra.screen.GuidePayload(
        0,
        0,
        "Find an old farmhouse.",
        "[ ] Recover seeds\n[ ] Make compost\n[ ] Restore dirt\n[ ] Harvest wheat",
        false);
  }

  public static void update(dev.reterra.screen.GuidePayload value) {
    progress = value;
  }

  public static void reset() {
    progress = initial();
    remembered = 0;
    lastObjective = -1;
  }

  public FieldGuideScreen() {
    super(Text.literal("Field Notes"));
  }

  public FieldGuideScreen(int page) {
    this();
    this.page = Math.clamp(page, 0, 5);
  }

  public static void open(net.minecraft.client.MinecraftClient client) {
    if (lastObjective != progress.chapter()) remembered = progress.chapter();
    lastObjective = progress.chapter();
    open(client, remembered);
  }

  public static void open(net.minecraft.client.MinecraftClient client, int chapter) {
    if (client.player != null
        && client.player.currentScreenHandler != client.player.playerScreenHandler)
      client.player.closeHandledScreen();
    client.setScreen(new FieldGuideScreen(chapter));
  }

  @Override
  protected void init() {
    panelWidth = Math.min(420, width - 16);
    panelHeight = Math.min(300, height - 16);
    left = (width - panelWidth) / 2;
    top = (height - panelHeight) / 2;
    addDrawableChild(
        FieldUi.button(
            Text.literal("Recipes"),
            b ->
                client.setScreen(
                    new RecipeBrowserScreen(
                        this,
                        switch (page) {
                          case 2 -> "soil_reclaimer";
                          case 3 -> "restoration_core";
                          case 4 -> "revival_chamber";
                          case 5 -> "heart_of_the_world";
                          default -> "compost_from_decay";
                        })),
            left + panelWidth - 82,
            top + 10,
            70,
            16));
    int chapterWidth = (panelWidth - 34) / 6;
    for (int i = 0; i < chapters.length; i++) {
      int chapter = i;
      chapters[i] =
          addDrawableChild(
              FieldUi.button(
                  Text.literal(LABELS[i]),
                  b -> {
                    page = remembered = chapter;
                    scroll = 0;
                  },
                  left + 12 + i * (chapterWidth + 2),
                  top + 34,
                  chapterWidth,
                  16));
      chapters[i].setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(TITLES[i])));
    }
    int buttonWidth = (panelWidth - 30) / 2;
    addDrawableChild(
        FieldUi.button(
            Text.literal("Current task"),
            b -> {
              page = remembered = progress.chapter();
              scroll = 0;
            },
            left + 12,
            top + panelHeight - 31,
            buttonWidth,
            19));
    addDrawableChild(
        FieldUi.button(
            Text.literal("Done"),
            b -> close(),
            left + 18 + buttonWidth,
            top + panelHeight - 31,
            buttonWidth,
            19));
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
    scroll = Math.clamp(scroll - (int) (vertical * 18), 0, maxScroll);
    return true;
  }

  @Override
  public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
    super.renderBackground(c, mouseX, mouseY, delta);
    FieldUi.panel(c, left, top, panelWidth, panelHeight, true);
    c.drawItem(new net.minecraft.item.ItemStack(Content.FIELD_JOURNAL), left + 12, top + 10);
    c.drawText(textRenderer, title, left + 34, top + 14, FieldUi.INK, false);
    for (int i = 0; i < chapters.length; i++) chapters[i].selected(page == i);
    c.drawText(textRenderer, TITLES[page], left + 12, top + 59, FieldUi.GREEN, false);
    int viewportTop = top + 75, viewportBottom = top + panelHeight - 42;
    c.enableScissor(left + 10, viewportTop, left + panelWidth - 10, viewportBottom);
    int y = viewportTop - scroll;
    if (page == progress.chapter()) {
      for (var line : textRenderer.wrapLines(Text.literal(progress.objective()), panelWidth - 30)) {
        c.drawText(textRenderer, line, left + 12, y, FieldUi.INK, false);
        y += 10;
      }
      y += 7;
      for (String task : progress.checklist().split("\n")) {
        boolean done = task.startsWith("[x]");
        FieldUi.check(c, left + 12, y, done);
        for (var line :
            textRenderer.wrapLines(
                Text.literal(task.length() > 4 ? task.substring(4) : task), panelWidth - 44)) {
          c.drawText(textRenderer, line, left + 25, y, done ? FieldUi.GREEN : FieldUi.INK, false);
          y += 10;
        }
        y += 2;
      }
      y += 7;
    }
    for (var paragraph : PAGES[page]) {
      for (var line : textRenderer.wrapLines(Text.literal(paragraph), panelWidth - 30)) {
        c.drawText(textRenderer, line, left + 12, y, FieldUi.INK, false);
        y += 10;
      }
      y += 9;
    }
    c.disableScissor();
    int contentHeight = y + scroll - viewportTop;
    int viewHeight = viewportBottom - viewportTop;
    maxScroll = Math.max(0, contentHeight - viewHeight);
    scroll = Math.min(scroll, maxScroll);
    if (maxScroll > 0) {
      int knob = Math.max(18, viewHeight * viewHeight / Math.max(viewHeight, contentHeight));
      int offset = (viewHeight - knob) * scroll / maxScroll;
      c.fill(left + panelWidth - 7, viewportTop, left + panelWidth - 5, viewportBottom, 0xFFB7B99E);
      c.fill(
          left + panelWidth - 7,
          viewportTop + offset,
          left + panelWidth - 5,
          viewportTop + offset + knob,
          FieldUi.GREEN);
    }
  }

  @Override
  public boolean shouldPause() {
    return false;
  }
}
