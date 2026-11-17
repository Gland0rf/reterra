package dev.reterra.client;

import dev.reterra.Content;
import dev.reterra.machine.MachineLogic;
import dev.reterra.machine.Multiblocks;
import dev.reterra.screen.MachineScreenHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class MachineScreen extends HandledScreen<MachineScreenHandler> {
  private ButtonWidget primary, secondary, recipes, info;
  private final FieldUi.Button[] tabs = new FieldUi.Button[4];
  private boolean browsingRecipes;
  private static final int INK = FieldUi.INK, MUTED = FieldUi.MUTED, GREEN = FieldUi.GREEN;

  public MachineScreen(MachineScreenHandler handler, PlayerInventory inventory, Text title) {
    super(handler, inventory, title);
    backgroundWidth = 300;
    backgroundHeight = 240;
    titleX = 12;
    titleY = 9;
    playerInventoryTitleX = 69;
    playerInventoryTitleY = 145;
  }

  @Override
  protected void init() {
    super.init();
    browsingRecipes = false;
    String[] tabLabels = {"Controls", "Build", "Habitat", "About"};
    for (int i = 0; i < 4; i++) {
      int id = i + 10;
      tabs[i] =
          addDrawableChild(
              FieldUi.button(
                  Text.literal(tabLabels[i]), b -> click(id), x + 12 + i * 70, y + 25, 66, 14));
    }
    primary =
        addDrawableChild(FieldUi.button(Text.empty(), b -> primary(), x + 12, y + 123, 134, 16));
    secondary =
        addDrawableChild(FieldUi.button(Text.empty(), b -> secondary(), x + 154, y + 123, 134, 16));
    recipes =
        addDrawableChild(
            FieldUi.button(Text.literal("Recipes"), b -> openRecipes(), x + 205, y + 7, 60, 15));
    info =
        addDrawableChild(FieldUi.button(Text.literal("?"), b -> click(13), x + 272, y + 7, 16, 15));
    info.setTooltip(
        net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("About this machine")));
    updateButtons();
  }

  private void openRecipes() {
    if (client == null) return;
    browsingRecipes = true;
    client.setScreen(new RecipeBrowserScreen(this, handler.kindName()));
  }

  boolean validContext() {
    return client != null && client.player != null && client.player.currentScreenHandler == handler;
  }

  @Override
  public void removed() {
    if (!browsingRecipes) super.removed();
  }

  private void click(int id) {
    if (client != null && client.interactionManager != null)
      client.interactionManager.clickButton(handler.syncId, id);
  }

  private void primary() {
    if (handler.value(45) != 0) {
      if (client != null && client.player != null) client.player.closeHandledScreen();
      return;
    }
    if (handler.value(26) != 0) click(10);
    else if (handler.kind() == 0 || handler.kind() == 2) click(0);
    else if (handler.kind() == 1 && handler.value(7) != 0) click(2);
    else click(1);
  }

  private void secondary() {
    if (handler.value(45) != 0) {
      click(4);
      return;
    }
    if (handler.value(26) == 0 && handler.kind() == 3 && handler.value(33) > 0) click(3);
    else if (handler.value(26) == 3) openRecipes();
    else if (handler.value(26) == 1) {
      if (handler.kind() == 0 || handler.kind() == 2) openRecipes();
      else click(12);
    } else if (handler.value(26) == 2) click(handler.kind() == 1 ? 4 : 11);
    else if (handler.kind() < 3 && handler.kind() != 1) click(2);
    else click(12);
  }

  private void updateButtons() {
    if (primary == null) return;
    recipes.visible = info.visible = handler.value(45) == 0;
    for (int i = 0; i < tabs.length; i++) tabs[i].selected(handler.value(26) == i);
    if (handler.value(45) != 0) {
      tabs[0].visible = tabs[1].visible = false;
      tabs[2].setX(x + 12);
      tabs[2].setWidth(134);
      tabs[2].setMessage(Text.literal("Site survey"));
      tabs[3].setX(x + 154);
      tabs[3].setWidth(134);
      tabs[3].setMessage(Text.literal("Estimate"));
      primary.setMessage(Text.literal("Close survey"));
      primary.active = true;
      secondary.setMessage(
          Text.literal(handler.value(42) != 0 ? "Hide preview border" : "Show preview border"));
      secondary.active = true;
      return;
    }
    int tabCount = handler.kind() == 0 || handler.kind() == 2 ? 2 : 3;
    for (int i = 0; i < tabs.length; i++) {
      tabs[i].visible = i < tabCount;
      tabs[i].setX(x + 12 + i * (tabCount == 2 ? 142 : 94));
      tabs[i].setWidth(tabCount == 2 ? 134 : 88);
    }
    boolean operate = handler.value(26) == 0;
    String label =
        !operate
            ? "Controls"
            : switch (handler.kind()) {
              case 0 -> handler.value(5) == 1 ? "Mode: make soil" : "Mode: restore terrain";
              case 1 ->
                  handler.value(7) == 0
                      ? "Register region"
                      : handler.value(6) == 1 ? "Resume restoration" : "Pause restoration";
              case 2 -> handler.value(39) == 1 ? "Mode: recover river" : "Mode: irrigate";
              case 3 -> handler.value(33) > 0 ? "Incubating..." : "Revive pair";
              default ->
                  handler.value(21) != 0
                      ? "Heart awakened"
                      : handler.value(53) > 0 ? "Awakening..." : "Awaken Heart";
            };
    primary.setMessage(Text.literal(label));
    primary.active = !operate || (!handler.readOnly() && handler.value(20) == 0);
    if (operate
        && handler.kind() == 4
        && (handler.value(21) != 0 || handler.value(53) > 0 || handler.value(25) < 4))
      primary.active = false;
    if (operate
        && handler.kind() == 3
        && (handler.value(33) > 0 || handler.value(8) != MachineLogic.Status.READY.ordinal()))
      primary.active = false;
    secondary.setMessage(
        Text.literal(
            handler.value(26) == 0 && handler.kind() == 3 && handler.value(33) > 0
                ? "Cancel incubation"
                : handler.value(26) == 1
                    ? handler.kind() == 0 || handler.kind() == 2 ? "Recipes" : "Habitat"
                    : handler.value(26) == 2
                        ? handler.kind() == 1
                            ? handler.value(42) != 0 ? "Hide boundary" : "Show boundary"
                            : "Build"
                        : handler.value(26) == 3
                            ? "Recipe"
                            : handler.kind() < 3 && handler.kind() != 1
                                ? (handler.value(6) == 1 ? "Resume" : "Pause")
                                : "Habitat"));
    secondary.active =
        handler.value(26) != 0
            || handler.kind() >= 3
            || handler.kind() == 1
            || (!handler.readOnly() && handler.value(20) == 0);
  }

  @Override
  protected void handledScreenTick() {
    super.handledScreenTick();
    updateButtons();
  }

  @Override
  public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    super.render(context, mouseX, mouseY, delta);
    drawMouseoverTooltip(context, mouseX, mouseY);
    if (mouseX >= x + 10 && mouseX < x + 290 && mouseY >= y + 42 && mouseY < y + 54)
      context.drawTooltip(textRenderer, statusDetails(), mouseX, mouseY);
    if (handler.value(26) == 0
        && handler.kind() < 4
        && mouseX >= x + 10
        && mouseX < x + 200
        && mouseY >= y + 109
        && mouseY < y + 121)
      context.drawTooltip(textRenderer, Text.literal("128 water units per bucket"), mouseX, mouseY);
    if (handler.value(26) == 0)
      for (int i = 0; i < 8; i++)
        if (handler.supplyVisible(i)
            && mouseX >= x + MachineScreenHandler.SLOT_X[i] - 1
            && mouseX < x + MachineScreenHandler.SLOT_X[i] + 17
            && mouseY >= y + 87
            && mouseY < y + 110
            && (handler.slots.get(i).getStack().isEmpty() || mouseY >= y + 105)) {
          var hint = wrap(slotHint(i).getString());
          if (i < 3) hint.add(Text.literal(reserveHint(i)).formatted(Formatting.GRAY));
          context.drawTooltip(textRenderer, hint, mouseX, mouseY);
        }
    if (handler.value(26) == 2
        && mouseX >= x + 12
        && mouseX < x + 288
        && mouseY >= y + 55
        && mouseY < y + 69) context.drawTooltip(textRenderer, wrap(nextGoal()), mouseX, mouseY);
    if (handler.value(26) == 2)
      for (int i = 0; i < 9; i++) {
        int px = x + 12 + i % 2 * 144, py = y + 73 + i / 2 * 10;
        if (mouseX >= px && mouseX < px + 140 && mouseY >= py && mouseY < py + 10)
          context.drawTooltip(textRenderer, wrap(goalHint(i)), mouseX, mouseY);
      }
    if (handler.value(26) == 1)
      for (var p : Multiblocks.parts(handler.kindName(), handler.value(32))) {
        int px = p.y() < 0 ? 82 : 48 + p.x() * 16, py = p.y() < 0 ? 108 : 78 + p.z() * 16;
        if (mouseX >= x + px && mouseX < x + px + 12 && mouseY >= y + py && mouseY < y + py + 12)
          context.drawTooltip(textRenderer, Text.literal(p.description()), mouseX, mouseY);
      }
  }

  @Override
  protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
    FieldUi.panel(c, x, y, 300, 240, false);
    c.fill(x + 6, y + 42, x + 294, y + 121, FieldUi.PAPER);
    c.fill(x + 6, y + 142, x + 294, y + 143, 0xFF9AA18F);
    for (int i = 8; i < handler.slots.size(); i++) {
      var s = handler.slots.get(i);
      slot(c, x + s.x, y + s.y);
    }
    if (handler.value(26) == 0) {
      for (int i = 0; i < 8; i++)
        if (handler.supplyVisible(i)) {
          int sx = x + MachineScreenHandler.SLOT_X[i], sy = y + 88;
          slot(c, sx, sy);
          if (handler.slots.get(i).getStack().isEmpty()) {
            c.drawItem(ghost(i), sx, sy);
            if (reserve(i) == 0) c.fill(sx, sy, sx + 16, sy + 16, 0x889B9F91);
          }
          if (i < 3) {
            c.fill(sx, sy + 18, sx + 16, sy + 20, 0xFF9AA18F);
            if (reserve(i) > 0)
              c.fill(
                  sx,
                  sy + 18,
                  sx
                      + Math.max(
                          1, Math.min(16, reserve(i) * 16 / (i == 0 ? 32 : i == 1 ? 128 : 256))),
                  sy + 20,
                  i == 2 ? 0xFF508EAC : GREEN);
          }
        }
    }
  }

  private void slot(DrawContext c, int px, int py) {
    FieldUi.slot(c, px, py);
  }

  @Override
  protected void drawForeground(DrawContext c, int mouseX, int mouseY) {
    c.drawText(
        textRenderer,
        textRenderer.trimToWidth(title.getString(), handler.value(45) == 0 ? 185 : 276),
        12,
        9,
        INK,
        false);
    c.drawText(textRenderer, playerInventoryTitle, 69, 145, MUTED, false);
    String status = statusText();
    c.drawText(
        textRenderer,
        textRenderer.trimToWidth(status, handler.value(26) == 1 ? 176 : 276),
        handler.value(26) == 1 ? 112 : 12,
        45,
        statusColor(),
        false);
    switch (handler.value(26)) {
      case 0 -> {
        FieldUi.wrapped(c, textRenderer, Text.literal(nextStep()), 12, 58, 276, INK);
        if (handler.kind() < 4) {
          for (int i = 0; i < 6; i++)
            if (handler.supplyVisible(i))
              c.drawText(
                  textRenderer,
                  supplyLabel(i),
                  MachineScreenHandler.SLOT_X[i] - 1,
                  77,
                  MUTED,
                  false);
          if (handler.kind() == 0 || handler.kind() == 2)
            c.drawText(textRenderer, "Output", 256, 77, MUTED, false);
          if (handler.kind() == 0 || handler.kind() == 2)
            c.drawText(textRenderer, ">", 213, 92, MUTED, false);
        }
        if (handler.kind() < 4) {
          c.drawText(
              textRenderer,
              "Water " + handler.value(3) + " / " + dev.reterra.machine.Balance.TANK_CAPACITY,
              12,
              111,
              0xFF315C70,
              false);
          c.fill(123, 111, 199, 119, 0xFF737F79);
          c.fill(
              123,
              111,
              123 + Math.min(76, handler.value(3) * 76 / dev.reterra.machine.Balance.TANK_CAPACITY),
              119,
              0xFF508EAC);
          if (handler.kind() == 3 && handler.value(33) > 0) {
            int seconds = (handler.value(33) - 1) / 20;
            c.drawText(textRenderer, seconds + " / 20s", 216, 111, GREEN, false);
            c.fill(210, 119, 210 + Math.min(76, (handler.value(33) - 1) * 76 / 400), 121, GREEN);
          }
          if (handler.kind() == 1 && handler.value(7) != 0 && handler.value(20) == 0) {
            c.drawText(
                textRenderer,
                handler.value(28) + "/" + trialSeconds() + "s",
                216,
                111,
                GREEN,
                false);
            c.fill(
                210, 119, 210 + Math.min(76, handler.value(28) * 76 / trialSeconds()), 121, GREEN);
          }
        }
        if (handler.kind() == 4 && handler.value(53) > 0) {
          c.drawText(
              textRenderer,
              "Awakening " + (handler.value(53) - 1) / 20 + " / 16s",
              12,
              97,
              GREEN,
              false);
          c.fill(12, 114, 288, 122, 0xFF10232C);
          c.fill(
              12,
              114,
              12
                  + Math.min(
                      276, handler.value(53) * 276 / dev.reterra.world.HeartAwakening.DURATION),
              122,
              GREEN);
        }
      }
      case 1 -> blueprint(c);
      case 2 -> habitat(c);
      case 3 -> help(c);
    }
  }

  private int statusColor() {
    return handler.value(8) == MachineLogic.Status.READY.ordinal()
            || handler.value(8) == MachineLogic.Status.COMPLETE.ordinal()
            || handler.value(8) == MachineLogic.Status.STABILIZING.ordinal()
            || handler.value(8) == MachineLogic.Status.INCUBATING.ordinal()
            || handler.value(8) == MachineLogic.Status.AWAKENING.ordinal()
            || handler.value(20) != 0
        ? GREEN
        : 0xFF84502C;
  }

  private String statusText() {
    if (handler.value(45) != 0)
      return familyName() + (handler.value(43) != 0 ? " - First sanctuary" : " - Full ecosystem");
    if (handler.value(26) == 1) return "Machine supports";
    if (handler.value(26) == 2)
      if (handler.kind() == 4) return "World recovery";
      else
        return family().name().charAt(0)
            + family().name().substring(1).toLowerCase(java.util.Locale.ROOT)
            + (handler.value(43) != 0 && handler.kind() == 1 ? " - First sanctuary" : " habitat");
    if (handler.value(26) == 3) return "About this machine";
    if (handler.value(24) == MachineLogic.Status.NO_SPACE.ordinal())
      return "Animals need room to emerge";
    if (handler.value(24) == MachineLogic.Status.REVIVED.ordinal()) return "A pair has emerged";
    return Text.translatable(
            "status.reterra."
                + MachineLogic.Status.values()[
                    Math.min(handler.value(8), MachineLogic.Status.values().length - 1)]
                    .name()
                    .toLowerCase(java.util.Locale.ROOT))
        .getString();
  }

  private List<Text> statusDetails() {
    List<Text> lines = new ArrayList<>();
    lines.add(Text.literal(statusText()).formatted(Formatting.GOLD));
    int issue = handler.value(9);
    var parts = Multiblocks.parts(handler.kindName(), handler.value(32));
    if (issue > 0 && issue <= parts.size())
      lines.add(Text.literal(parts.get(issue - 1).description()));
    if (handler.kind() == 1) {
      lines.add(
          Text.literal(
              "Soil "
                  + handler.value(10)
                  + "% / "
                  + targets()[0]
                  + "%; grass "
                  + handler.value(11)
                  + "% / "
                  + targets()[1]
                  + "%"));
      lines.add(Text.literal("Region: 48 x 48 blocks"));
      lines.add(
          Text.literal(
              "Animal types: "
                  + handler.value(29)
                  + " / 2; stable "
                  + handler.value(28)
                  + " / "
                  + trialSeconds()
                  + " sec"));
    }
    if (handler.kind() < 4)
      for (int slot = 0; slot < 3; slot++)
        if (handler.supplyVisible(slot)) lines.add(Text.literal(reserveHint(slot)));
    if (handler.value(41) != 0)
      lines.add(Text.literal("Restoration research improves compost and fuel yield."));
    return lines;
  }

  private void blueprint(DrawContext c) {
    blueprintIcon(
        c,
        new ItemStack(net.minecraft.registry.Registries.ITEM.get(Content.id(handler.kindName()))),
        48,
        78);
    var parts = Multiblocks.parts(handler.kindName(), handler.value(32));
    int issue = handler.value(9);
    for (int i = 0; i < parts.size(); i++) {
      var p = parts.get(i);
      int px = p.y() < 0 ? 82 : 48 + p.x() * 16, py = p.y() < 0 ? 108 : 78 + p.z() * 16;
      c.fill(px - 1, py - 1, px + 13, py + 13, issue == i + 1 ? 0xFFA5754D : 0xFF465B43);
      blueprintIcon(c, new ItemStack(p.block()), px, py);
    }
    c.drawText(textRenderer, "Below", 78, 98, MUTED, false);
    String detail =
        issue == 0
            ? "Ready. Open Controls to add supplies."
            : parts.get(Math.min(parts.size() - 1, issue - 1)).description();
    FieldUi.wrapped(c, textRenderer, Text.literal(detail), 112, 57, 174, INK);
    var materials = new java.util.LinkedHashMap<net.minecraft.block.Block, Integer>();
    for (var part : parts) materials.merge(part.block(), 1, Integer::sum);
    int row = 0;
    for (var material : materials.entrySet())
      c.drawText(
          textRenderer,
          textRenderer.trimToWidth(
              material.getValue() + " " + material.getKey().getName().getString(), 174),
          112,
          80 + row++ * 10,
          MUTED,
          false);
    c.drawText(
        textRenderer,
        materials.containsKey(net.minecraft.block.Blocks.WATER_CAULDRON)
            ? "Any facing. Fill cauldrons."
            : "Any facing works.",
        112,
        112,
        MUTED,
        false);
  }

  private void habitat(DrawContext c) {
    if (handler.kind() == 4) {
      c.drawText(textRenderer, "Reclaimed ecosystems", 12, 59, INK, false);
      for (var f : dev.reterra.ecology.Ecology.Family.values()) {
        boolean done = (handler.value(19) & 1 << f.ordinal()) != 0;
        FieldUi.check(c, 12, 72 + f.ordinal() * 12, done);
        c.drawText(
            textRenderer,
            ""
                + f.name().charAt(0)
                + f.name().substring(1).toLowerCase(java.util.Locale.ROOT)
                + ": "
                + dev.reterra.ecology.Ecology.benefit(f),
            25,
            72 + f.ordinal() * 12,
            done ? GREEN : MUTED,
            false);
      }
      return;
    }
    c.drawText(textRenderer, textRenderer.trimToWidth(nextGoal(), 276), 12, 57, INK, false);
    String[] labels = {
      "Soil",
      "Grass",
      "Water",
      "Crops",
      "Leaf cover",
      "Flowers",
      "Plant types",
      "Animal types",
      bonusLabel()
    };
    int[] values = measurements();
    int[] targets = targets();
    for (int i = 0; i < 9; i++) {
      int px = 12 + (i % 2) * 144, py = 73 + (i / 2) * 10;
      if (i == 8 && targets[i] == 0) continue;
      if (i == 7 && handler.kind() == 3) {
        c.drawText(textRenderer, "No adults needed", px, py, GREEN, false);
        continue;
      }
      int color = values[i] >= targets[i] ? GREEN : MUTED;
      c.drawText(
          textRenderer,
          targets[i] == 0
              ? labels[i] + ": optional"
              : labels[i] + " " + values[i] + " / " + targets[i] + (i < 2 ? "%" : ""),
          px,
          py,
          color,
          false);
      c.fill(px, py + 9, px + 132, py + 10, 0xFFA3AB96);
      c.fill(
          px,
          py + 9,
          px + Math.min(132, values[i] * 132 / Math.max(1, targets[i])),
          py + 10,
          color);
    }
  }

  private int[] targets() {
    var g = dev.reterra.ecology.Ecology.goals(family(), handler.value(43) != 0);
    return handler.kind() == 3
        ? new int[] {35, 15, 4, Math.min(4, g.crops()), 1, 1, 3, 0, 0}
        : new int[] {
          g.soil(),
          g.grass(),
          g.water(),
          g.crops(),
          g.trees(),
          g.flowers(),
          g.species(),
          2,
          Math.max(g.treeTypes(), Math.max(g.wetland(), g.slopes()))
        };
  }

  private dev.reterra.ecology.Ecology.Family family() {
    return dev.reterra.ecology.Ecology.Family.values()[Math.clamp(handler.value(23), 0, 3)];
  }

  private int[] measurements() {
    return new int[] {
      handler.value(10),
      handler.value(11),
      handler.value(12),
      handler.value(13),
      handler.value(14),
      handler.value(15),
      handler.value(16),
      handler.value(29),
      switch (family()) {
        case FOREST -> handler.value(36);
        case SWAMP -> handler.value(37);
        case HIGHLANDS -> handler.value(38);
        default -> 0;
      }
    };
  }

  private String bonusLabel() {
    return switch (family()) {
      case FOREST -> "Tree types";
      case SWAMP -> "Waterside";
      case HIGHLANDS -> "Fertile slopes";
      default -> "";
    };
  }

  private String nextGoal() {
    if (handler.value(45) != 0) {
      if (handler.value(51) != 0) return "Overlaps an existing project. Choose another site.";
      if (handler.value(30) < 9) return "Surveying the site: " + handler.value(30) + "/9 areas.";
      return handler.value(50) > 0
          ? "Rocky ground. Place " + handler.value(50) + " dirt blocks by hand."
          : "Suitable ground. Estimate shows the supplies still needed.";
    }
    if (handler.value(20) != 0)
      return "Reclaimed - recovery area "
          + ((handler.value(34) * 2 + 1) * 16)
          + " x "
          + ((handler.value(34) * 2 + 1) * 16)
          + " blocks.";
    if (handler.value(18) < 128 || (handler.kind() != 3 && handler.value(30) < 9))
      return "Checking the habitat: " + handler.value(30) + "/9 areas.";
    int[] values = measurements();
    int[] goals = targets();
    for (int i = 0; i < goals.length; i++) if (values[i] < goals[i]) return goalHint(i);
    if (handler.kind() == 3) return "Habitat ready. Add a sample to the Chamber.";
    if (handler.value(20) != 0)
      return "Reclaimed! Scarred soil and dead trunks now recover naturally.";
    if (handler.value(31) == 0)
      return "Trial paused at "
          + handler.value(28)
          + "/"
          + trialSeconds()
          + "s. Stay nearby to continue.";
    if (handler.value(6) != 0)
      return "Trial paused at "
          + handler.value(28)
          + "/"
          + trialSeconds()
          + "s. Resume the Core to continue.";
    if (handler.value(9) != 0) return "Trial paused: repair the Core assembly.";
    if (handler.value(47) < 3)
      return "Optional: "
          + ((handler.value(46) & 2) == 0
              ? dev.reterra.ecology.HabitatPlants.careHint(family(), handler.value(52))
              : (handler.value(46) & 4) == 0 ? "feed adults" : "tend crops")
          + " (+15s).";
    return "Stabilizing "
        + handler.value(28)
        + "/"
        + trialSeconds()
        + "s. Keep the habitat healthy.";
  }

  private int trialSeconds() {
    return handler.value(44) > 0 ? handler.value(44) : handler.value(43) != 0 ? 90 : 180;
  }

  private String familyName() {
    return family().name().charAt(0)
        + family().name().substring(1).toLowerCase(java.util.Locale.ROOT);
  }

  private String goalHint(int i) {
    return switch (i) {
      case 0 -> "Run the Core, or place dirt by hand.";
      case 1 -> "Use grass culture on dirt; let the patches spread.";
      case 2 -> "Make a small pond inside the region.";
      case 3 -> "Plant more crops within the 48 x 48 region.";
      case 4 -> "Grow trees for leaf cover.";
      case 5 -> "Compost or bone meal on grass grows flowers.";
      case 6 -> "Grow different crops, flowers or tree types.";
      case 8 ->
          switch (family()) {
            case FOREST -> "Grow two tree species: leaf types count.";
            case SWAMP -> "Plant cane, saplings, mushrooms or lilies beside surface water.";
            case HIGHLANDS -> "Restore soil on slopes, or build planted terraces.";
            default -> "";
          };
      default ->
          handler.kind() == 3
              ? "The Chamber can revive the first pair in an empty habitat."
              : "Establish two adult animal types.";
    };
  }

  private String supplyLabel(int i) {
    return switch (i) {
      case 0 -> handler.kind() == 2 ? "Filter" : "Compost";
      case 1 -> "Fuel";
      case 2 -> "Water";
      case 3 ->
          switch (handler.kind()) {
            case 0 -> "Input";
            case 1 -> "Return";
            case 2 -> "Bucket";
            default -> "Sample";
          };
      case 4 -> "Food";
      default -> "Culture";
    };
  }

  private int reserve(int slot) {
    return slot >= 0 && slot < 3 ? handler.value(slot + 1) : 0;
  }

  private String reserveHint(int slot) {
    return switch (slot) {
      case 0 ->
          handler.kind() == 2
              ? "Filter: " + reserve(0) + " cycles left"
              : "Compost: " + reserve(0) + " soil blocks left";
      case 1 -> "Fuel: " + reserve(1) + " actions left";
      default -> "Water: " + reserve(2) + " / 256";
    };
  }

  private String nextStep() {
    var status =
        MachineLogic.Status.values()[
            Math.clamp(handler.value(8), 0, MachineLogic.Status.values().length - 1)];
    return switch (status) {
      case ASSEMBLY -> "Add the missing supports shown in Build.";
      case REGISTER ->
          handler.kind() == 3
              ? "Place inside a registered region."
              : "Register this region to begin.";
      case HABITAT -> nextGoal();
      case SAMPLE -> "Recover a preserved sample from a biological archive.";
      case FOOD -> foodHint();
      case NUTRIENTS -> "Add a nutrient culture.";
      case COMPOST -> handler.kind() == 2 ? "Add a charcoal filter." : "Add organic compost.";
      case FUEL -> "Add coal, charcoal or nutrient culture.";
      case WATER -> "Add a water bucket.";
      case MATERIAL ->
          handler.kind() == 2 ? "Add an empty bucket to fill." : "Add coarse dirt or gravel.";
      case OUTPUT -> "Collect the output to continue.";
      case PAUSED -> "Resume when you are ready.";
      case OVERLAP -> "Choose a site outside the other region.";
      case NO_SPACE -> "Leave two air blocks above nearby grass.";
      case REVIVED -> "Give the pair room and food to breed.";
      case CULTURE -> "Grass culture required.";
      case READY ->
          handler.kind() == 1
              ? nextGoal()
              : handler.kind() == 3
                  ? "Ready to incubate. Clear grass beside the chamber."
                  : handler.kind() == 0
                      ? "Turns gravel or coarse dirt into dirt."
                      : handler.value(39) != 0
                          ? "Refills riverbeds within 12 blocks. Restored: " + handler.value(40)
                          : "Fills nearby machine tanks within "
                              + dev.reterra.machine.EcosystemBenefits.pumpReach(handler.value(19))
                              + " blocks.";
      case OBJECTIVES ->
          "Reclaim plains, forest, swamp and highlands: " + handler.value(25) + "/4 ecosystems.";
      case RECLAIMED -> nextGoal();
      case STABILIZING -> nextGoal();
      case INCUBATING -> "Keep the habitat healthy.";
      case AWAKENING -> "Step outside to watch the Heart awaken.";
      case POPULATION -> "Adult population is at capacity (16).";
      case COMPLETE -> "Recovery aura: 272 x 272. Completed regions spread twice as fast.";
      default -> "";
    };
  }

  private void help(DrawContext c) {
    if (handler.value(45) != 0) {
      int soil = handler.value(48), grass = handler.value(49), work = soil;
      int compost = dev.reterra.machine.EcosystemBenefits.compost(handler.value(19));
      int fuel = dev.reterra.machine.EcosystemBenefits.fuel(handler.value(19), false);
      String[] estimate = {
        "Soil still needed: " + soil + " blocks",
        "Grass still needed: " + grass + " blocks",
        "Approx: " + ((soil + compost - 1) / compost) + " compost",
        "Fuel: "
            + ((work + fuel - 1) / fuel)
            + " coal; water: "
            + ((work + 127) / 128)
            + " buckets",
        "Manual soil placement: " + handler.value(50),
        "Plant grass cultures in several patches."
      };
      for (int i = 0; i < estimate.length; i++)
        c.drawText(textRenderer, estimate[i], 12, 57 + i * 10, INK, false);
      return;
    }
    String text =
        switch (handler.kind()) {
          case 0 ->
              "Processes gravel or coarse dirt into soil. Terrain mode restores a 15 x 15 area."
                  + " Compost: "
                  + dev.reterra.machine.EcosystemBenefits.compost(handler.value(19))
                  + " blocks.";
          case 1 ->
              "Restores soil in a 48 x 48 region. Plant grass cultures yourself. Needs a furnace"
                  + " below.";
          case 2 ->
              "Place beside source water and add filters and fuel. Irrigation fills tanks within "
                  + dev.reterra.machine.EcosystemBenefits.pumpReach(handler.value(19))
                  + " blocks. River mode refills damaged channels.";
          case 3 ->
              "Add a sample, nutrient culture and water. "
                  + foodHint()
                  + " Incubation takes 20 seconds.";
          default ->
              "Four reclaimed ecosystems awaken a permanent 272 x 272 recovery aura. Nature regrows"
                  + " in loaded terrain; completed regions spread twice as fast.";
        };
    FieldUi.wrapped(c, textRenderer, Text.literal(text), 12, 59, 276, INK);
  }

  private ItemStack ghost(int slot) {
    return new ItemStack(
        switch (slot) {
          case 0 -> handler.kind() == 2 ? Content.FILTER : Content.COMPOST;
          case 1 -> Items.CHARCOAL;
          case 2 -> Items.WATER_BUCKET;
          case 3 ->
              switch (handler.kind()) {
                case 0 -> Items.GRAVEL;
                case 1 -> Content.CULTURE;
                case 2 -> Items.BUCKET;
                default -> Content.SHEEP_SAMPLE;
              };
          case 4 ->
              MachineLogic.sample(handler.slots.get(3).getStack())
                  ? MachineLogic.food(handler.slots.get(3).getStack())
                  : Items.AIR;
          case 5 -> Content.NUTRIENTS;
          case 6 -> handler.kind() == 2 ? Items.WATER_BUCKET : Items.DIRT;
          default -> Items.BUCKET;
        });
  }

  private Text slotHint(int slot) {
    return Text.literal(
        switch (slot) {
          case 0 ->
              handler.kind() == 2
                  ? "Charcoal filter: 8 pump cycles"
                  : "Compost: "
                      + dev.reterra.machine.EcosystemBenefits.compost(handler.value(19))
                      + " soil blocks";
          case 1 ->
              "Coal/charcoal: "
                  + dev.reterra.machine.EcosystemBenefits.fuel(
                      handler.kind() < 2 ? handler.value(19) : 0, false)
                  + " work";
          case 2 -> "Water bucket: 128 water units";
          case 3 ->
              switch (handler.kind()) {
                case 0 -> "Coarse dirt or gravel";
                case 1 -> "Returned grass cultures";
                case 2 -> "Empty buckets";
                default -> "Animal sample, preserved in biological archives";
              };
          case 4 -> foodHint();
          case 5 -> "Nutrient culture";
          case 6 -> "Output";
          default -> "Buckets from an older machine";
        });
  }

  private String foodHint() {
    ItemStack sample = handler.slots.get(3).getStack();
    if (!MachineLogic.sample(sample)) return "Insert an animal sample first.";
    return sample.isOf(Content.BEE_SAMPLE)
        ? "Add 2 flowers."
        : sample.isOf(Content.CHICKEN_SAMPLE) ? "Add 4 wheat seeds." : "Add 4 wheat.";
  }

  private void blueprintIcon(DrawContext c, ItemStack item, int px, int py) {
    FieldUi.push(c, px, py, 0.75f);
    c.drawItem(item, 0, 0);
    FieldUi.pop(c);
  }

  private List<Text> wrap(String text) {
    List<Text> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : text.split(" ")) {
      if (line.length() + word.length() > 46) {
        lines.add(Text.literal(line.toString()));
        line.setLength(0);
      }
      if (!line.isEmpty()) line.append(' ');
      line.append(word);
    }
    if (!line.isEmpty()) lines.add(Text.literal(line.toString()));
    return lines;
  }
}
