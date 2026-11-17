package dev.reterra.client;

import dev.reterra.Content;
import dev.reterra.machine.MachineLogic;
import dev.reterra.machine.Multiblocks;
import dev.reterra.screen.MachineScreenHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MachineScreen extends AbstractContainerScreen<MachineScreenHandler> {
  private Button primary, secondary, recipes, info;
  private final FieldUi.Button[] tabs = new FieldUi.Button[4];
  private boolean browsingRecipes;
  private static final int INK = FieldUi.INK, MUTED = FieldUi.MUTED, GREEN = FieldUi.GREEN;

  public MachineScreen(MachineScreenHandler handler, Inventory inventory, Component title) {
    super(handler, inventory, title, 300, 240);
    titleLabelX = 12;
    titleLabelY = 9;
    inventoryLabelX = 69;
    inventoryLabelY = 145;
  }

  @Override
  protected void init() {
    super.init();
    browsingRecipes = false;
    String[] tabLabels = {"Controls", "Build", "Habitat", "About"};
    for (int i = 0; i < 4; i++) {
      int id = i + 10;
      tabs[i] =
          addRenderableWidget(
              FieldUi.button(
                  Component.literal(tabLabels[i]), b -> click(id), leftPos + 12 + i * 70, topPos + 25, 66, 14));
    }
    primary =
        addRenderableWidget(FieldUi.button(Component.empty(), b -> primary(), leftPos + 12, topPos + 123, 134, 16));
    secondary =
        addRenderableWidget(FieldUi.button(Component.empty(), b -> secondary(), leftPos + 154, topPos + 123, 134, 16));
    recipes =
        addRenderableWidget(
            FieldUi.button(Component.literal("Recipes"), b -> openRecipes(), leftPos + 205, topPos + 7, 60, 15));
    info =
        addRenderableWidget(FieldUi.button(Component.literal("?"), b -> click(13), leftPos + 272, topPos + 7, 16, 15));
    info.setTooltip(
        net.minecraft.client.gui.components.Tooltip.create(Component.literal("About this machine")));
    updateButtons();
  }

  private void openRecipes() {
    if (minecraft == null) return;
    browsingRecipes = true;
    dev.reterra.client.ClientAccess.screen(minecraft, new RecipeBrowserScreen(this, menu.kindName()));
  }

  boolean validContext() {
    return minecraft != null && minecraft.player != null && minecraft.player.containerMenu == menu;
  }

  @Override
  public void removed() {
    if (!browsingRecipes) super.removed();
  }

  private void click(int id) {
    if (minecraft != null && minecraft.gameMode != null)
      minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
  }

  private void primary() {
    if (menu.value(45) != 0) {
      if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
      return;
    }
    if (menu.value(26) != 0) click(10);
    else if (menu.kind() == 0 || menu.kind() == 2) click(0);
    else if (menu.kind() == 1 && menu.value(7) != 0) click(2);
    else click(1);
  }

  private void secondary() {
    if (menu.value(45) != 0) {
      click(4);
      return;
    }
    if (menu.value(26) == 0 && menu.kind() == 3 && menu.value(33) > 0) click(3);
    else if (menu.value(26) == 3) openRecipes();
    else if (menu.value(26) == 1) {
      if (menu.kind() == 0 || menu.kind() == 2) openRecipes();
      else click(12);
    } else if (menu.value(26) == 2) click(menu.kind() == 1 ? 4 : 11);
    else if (menu.kind() < 3 && menu.kind() != 1) click(2);
    else click(12);
  }

  private void updateButtons() {
    if (primary == null) return;
    recipes.visible = info.visible = menu.value(45) == 0;
    for (int i = 0; i < tabs.length; i++) tabs[i].selected(menu.value(26) == i);
    if (menu.value(45) != 0) {
      tabs[0].visible = tabs[1].visible = false;
      tabs[2].setX(leftPos + 12);
      tabs[2].setWidth(134);
      tabs[2].setMessage(Component.literal("Site survey"));
      tabs[3].setX(leftPos + 154);
      tabs[3].setWidth(134);
      tabs[3].setMessage(Component.literal("Estimate"));
      primary.setMessage(Component.literal("Close survey"));
      primary.active = true;
      secondary.setMessage(
          Component.literal(menu.value(42) != 0 ? "Hide preview border" : "Show preview border"));
      secondary.active = true;
      return;
    }
    int tabCount = menu.kind() == 0 || menu.kind() == 2 ? 2 : 3;
    for (int i = 0; i < tabs.length; i++) {
      tabs[i].visible = i < tabCount;
      tabs[i].setX(leftPos + 12 + i * (tabCount == 2 ? 142 : 94));
      tabs[i].setWidth(tabCount == 2 ? 134 : 88);
    }
    boolean operate = menu.value(26) == 0;
    String label =
        !operate
            ? "Controls"
            : switch (menu.kind()) {
              case 0 -> menu.value(5) == 1 ? "Mode: make soil" : "Mode: restore terrain";
              case 1 ->
                  menu.value(7) == 0
                      ? "Register region"
                      : menu.value(6) == 1 ? "Resume restoration" : "Pause restoration";
              case 2 -> menu.value(39) == 1 ? "Mode: recover river" : "Mode: irrigate";
              case 3 -> menu.value(33) > 0 ? "Incubating..." : "Revive pair";
              default ->
                  menu.value(21) != 0
                      ? "Heart awakened"
                      : menu.value(53) > 0 ? "Awakening..." : "Awaken Heart";
            };
    primary.setMessage(Component.literal(label));
    primary.active = !operate || (!menu.readOnly() && menu.value(20) == 0);
    if (operate
        && menu.kind() == 4
        && (menu.value(21) != 0 || menu.value(53) > 0 || menu.value(25) < 4))
      primary.active = false;
    if (operate
        && menu.kind() == 3
        && (menu.value(33) > 0 || menu.value(8) != MachineLogic.Status.READY.ordinal()))
      primary.active = false;
    secondary.setMessage(
        Component.literal(
            menu.value(26) == 0 && menu.kind() == 3 && menu.value(33) > 0
                ? "Cancel incubation"
                : menu.value(26) == 1
                    ? menu.kind() == 0 || menu.kind() == 2 ? "Recipes" : "Habitat"
                    : menu.value(26) == 2
                        ? menu.kind() == 1
                            ? menu.value(42) != 0 ? "Hide boundary" : "Show boundary"
                            : "Build"
                        : menu.value(26) == 3
                            ? "Recipe"
                            : menu.kind() < 3 && menu.kind() != 1
                                ? (menu.value(6) == 1 ? "Resume" : "Pause")
                                : "Habitat"));
    secondary.active =
        menu.value(26) != 0
            || menu.kind() >= 3
            || menu.kind() == 1
            || (!menu.readOnly() && menu.value(20) == 0);
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    updateButtons();
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    super.extractRenderState(context, mouseX, mouseY, delta);
    if (mouseX >= leftPos + 10 && mouseX < leftPos + 290 && mouseY >= topPos + 42 && mouseY < topPos + 54)
      context.setComponentTooltipForNextFrame(font, statusDetails(), mouseX, mouseY);
    if (menu.value(26) == 0
        && menu.kind() < 4
        && mouseX >= leftPos + 10
        && mouseX < leftPos + 200
        && mouseY >= topPos + 109
        && mouseY < topPos + 121)
      context.setTooltipForNextFrame(font, Component.literal("128 water units per bucket"), mouseX, mouseY);
    if (menu.value(26) == 0)
      for (int i = 0; i < 8; i++)
        if (menu.supplyVisible(i)
            && mouseX >= leftPos + MachineScreenHandler.SLOT_X[i] - 1
            && mouseX < leftPos + MachineScreenHandler.SLOT_X[i] + 17
            && mouseY >= topPos + 87
            && mouseY < topPos + 110
            && (menu.slots.get(i).getItem().isEmpty() || mouseY >= topPos + 105)) {
          var hint = wrap(slotHint(i).getString());
          if (i < 3) hint.add(Component.literal(reserveHint(i)).withStyle(ChatFormatting.GRAY));
          context.setComponentTooltipForNextFrame(font, hint, mouseX, mouseY);
        }
    if (menu.value(26) == 2
        && mouseX >= leftPos + 12
        && mouseX < leftPos + 288
        && mouseY >= topPos + 55
        && mouseY < topPos + 69) context.setComponentTooltipForNextFrame(font, wrap(nextGoal()), mouseX, mouseY);
    if (menu.value(26) == 2)
      for (int i = 0; i < 9; i++) {
        int px = leftPos + 12 + i % 2 * 144, py = topPos + 73 + i / 2 * 10;
        if (mouseX >= px && mouseX < px + 140 && mouseY >= py && mouseY < py + 10)
          context.setComponentTooltipForNextFrame(font, wrap(goalHint(i)), mouseX, mouseY);
      }
    if (menu.value(26) == 1)
      for (var p : Multiblocks.parts(menu.kindName(), menu.value(32))) {
        int px = p.y() < 0 ? 82 : 48 + p.x() * 16, py = p.y() < 0 ? 108 : 78 + p.z() * 16;
        if (mouseX >= leftPos + px && mouseX < leftPos + px + 12 && mouseY >= topPos + py && mouseY < topPos + py + 12)
          context.setTooltipForNextFrame(font, Component.literal(p.description()), mouseX, mouseY);
      }
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
    FieldUi.panel(c, leftPos, topPos, 300, 240, false);
    c.fill(leftPos + 6, topPos + 42, leftPos + 294, topPos + 121, FieldUi.PAPER);
    c.fill(leftPos + 6, topPos + 142, leftPos + 294, topPos + 143, 0xFF9AA18F);
    for (int i = 8; i < menu.slots.size(); i++) {
      var s = menu.slots.get(i);
      slot(c, leftPos + s.x, topPos + s.y);
    }
    if (menu.value(26) == 0) {
      for (int i = 0; i < 8; i++)
        if (menu.supplyVisible(i)) {
          int sx = leftPos + MachineScreenHandler.SLOT_X[i], sy = topPos + 88;
          slot(c, sx, sy);
          if (menu.slots.get(i).getItem().isEmpty()) {
            c.item(ghost(i), sx, sy);
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

  private void slot(GuiGraphicsExtractor c, int px, int py) {
    FieldUi.slot(c, px, py);
  }

  @Override
  protected void extractLabels(GuiGraphicsExtractor c, int mouseX, int mouseY) {
    c.text(
        font,
        font.plainSubstrByWidth(title.getString(), menu.value(45) == 0 ? 185 : 276),
        12,
        9,
        INK,
        false);
    c.text(font, playerInventoryTitle, 69, 145, MUTED, false);
    String status = statusText();
    c.text(
        font,
        font.plainSubstrByWidth(status, menu.value(26) == 1 ? 176 : 276),
        menu.value(26) == 1 ? 112 : 12,
        45,
        statusColor(),
        false);
    switch (menu.value(26)) {
      case 0 -> {
        FieldUi.wrapped(c, font, Component.literal(nextStep()), 12, 58, 276, INK);
        if (menu.kind() < 4) {
          for (int i = 0; i < 6; i++)
            if (menu.supplyVisible(i))
              c.text(
                  font,
                  supplyLabel(i),
                  MachineScreenHandler.SLOT_X[i] - 1,
                  77,
                  MUTED,
                  false);
          if (menu.kind() == 0 || menu.kind() == 2)
            c.text(font, "Output", 256, 77, MUTED, false);
          if (menu.kind() == 0 || menu.kind() == 2)
            c.text(font, ">", 213, 92, MUTED, false);
        }
        if (menu.kind() < 4) {
          c.text(
              font,
              "Water " + menu.value(3) + " / " + dev.reterra.machine.Balance.TANK_CAPACITY,
              12,
              111,
              0xFF315C70,
              false);
          c.fill(123, 111, 199, 119, 0xFF737F79);
          c.fill(
              123,
              111,
              123 + Math.min(76, menu.value(3) * 76 / dev.reterra.machine.Balance.TANK_CAPACITY),
              119,
              0xFF508EAC);
          if (menu.kind() == 3 && menu.value(33) > 0) {
            int seconds = (menu.value(33) - 1) / 20;
            c.text(font, seconds + " / 20s", 216, 111, GREEN, false);
            c.fill(210, 119, 210 + Math.min(76, (menu.value(33) - 1) * 76 / 400), 121, GREEN);
          }
          if (menu.kind() == 1 && menu.value(7) != 0 && menu.value(20) == 0) {
            c.text(
                font,
                menu.value(28) + "/" + trialSeconds() + "s",
                216,
                111,
                GREEN,
                false);
            c.fill(
                210, 119, 210 + Math.min(76, menu.value(28) * 76 / trialSeconds()), 121, GREEN);
          }
        }
        if (menu.kind() == 4 && menu.value(53) > 0) {
          c.text(
              font,
              "Awakening " + (menu.value(53) - 1) / 20 + " / 16s",
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
                      276, menu.value(53) * 276 / dev.reterra.world.HeartAwakening.DURATION),
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
    return menu.value(8) == MachineLogic.Status.READY.ordinal()
            || menu.value(8) == MachineLogic.Status.COMPLETE.ordinal()
            || menu.value(8) == MachineLogic.Status.STABILIZING.ordinal()
            || menu.value(8) == MachineLogic.Status.INCUBATING.ordinal()
            || menu.value(8) == MachineLogic.Status.AWAKENING.ordinal()
            || menu.value(20) != 0
        ? GREEN
        : 0xFF84502C;
  }

  private String statusText() {
    if (menu.value(45) != 0)
      return familyName() + (menu.value(43) != 0 ? " - First sanctuary" : " - Full ecosystem");
    if (menu.value(26) == 1) return "Machine supports";
    if (menu.value(26) == 2)
      if (menu.kind() == 4) return "World recovery";
      else
        return family().name().charAt(0)
            + family().name().substring(1).toLowerCase(java.util.Locale.ROOT)
            + (menu.value(43) != 0 && menu.kind() == 1 ? " - First sanctuary" : " habitat");
    if (menu.value(26) == 3) return "About this machine";
    if (menu.value(24) == MachineLogic.Status.NO_SPACE.ordinal())
      return "Animals need room to emerge";
    if (menu.value(24) == MachineLogic.Status.REVIVED.ordinal()) return "A pair has emerged";
    if (menu.kind() == 1 && menu.value(8) == MachineLogic.Status.READY.ordinal())
      return Component.translatable("status.reterra.restoring").getString();
    return Component.translatable(
            "status.reterra."
                + MachineLogic.Status.values()[
                    Math.min(menu.value(8), MachineLogic.Status.values().length - 1)]
                    .name()
                    .toLowerCase(java.util.Locale.ROOT))
        .getString();
  }

  private List<Component> statusDetails() {
    List<Component> lines = new ArrayList<>();
    lines.add(Component.literal(statusText()).withStyle(ChatFormatting.GOLD));
    int issue = menu.value(9);
    var parts = Multiblocks.parts(menu.kindName(), menu.value(32));
    if (issue > 0 && issue <= parts.size())
      lines.add(Component.literal(parts.get(issue - 1).description()));
    if (menu.kind() == 1) {
      lines.add(
          Component.literal(
              "Soil "
                  + menu.value(10)
                  + "% / "
                  + targets()[0]
                  + "%; grass "
                  + menu.value(11)
                  + "% / "
                  + targets()[1]
                  + "%"));
      lines.add(Component.literal("Region: 48 x 48 blocks"));
      lines.add(
          Component.literal(
              "Animal types: "
                  + menu.value(29)
                  + " / 2; stable "
                  + menu.value(28)
                  + " / "
                  + trialSeconds()
                  + " sec"));
    }
    if (menu.kind() < 4)
      for (int slot = 0; slot < 3; slot++)
        if (menu.supplyVisible(slot)) lines.add(Component.literal(reserveHint(slot)));
    if (menu.value(41) != 0)
      lines.add(Component.literal("Restoration research improves compost and fuel yield."));
    return lines;
  }

  private void blueprint(GuiGraphicsExtractor c) {
    blueprintIcon(
        c,
        new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Content.id(menu.kindName()))),
        48,
        78);
    var parts = Multiblocks.parts(menu.kindName(), menu.value(32));
    int issue = menu.value(9);
    for (int i = 0; i < parts.size(); i++) {
      var p = parts.get(i);
      int px = p.y() < 0 ? 82 : 48 + p.x() * 16, py = p.y() < 0 ? 108 : 78 + p.z() * 16;
      c.fill(px - 1, py - 1, px + 13, py + 13, issue == i + 1 ? 0xFFA5754D : 0xFF465B43);
      blueprintIcon(c, new ItemStack(p.block()), px, py);
    }
    c.text(font, "Below", 78, 98, MUTED, false);
    String detail =
        issue == 0
            ? "Ready. Open Controls to add supplies."
            : parts.get(Math.min(parts.size() - 1, issue - 1)).description();
    FieldUi.wrapped(c, font, Component.literal(detail), 112, 57, 174, INK);
    var materials = new java.util.LinkedHashMap<net.minecraft.world.level.block.Block, Integer>();
    for (var part : parts) materials.merge(part.block(), 1, Integer::sum);
    int row = 0;
    for (var material : materials.entrySet())
      c.text(
          font,
          font.plainSubstrByWidth(
              material.getValue() + " " + material.getKey().getName().getString(), 174),
          112,
          80 + row++ * 10,
          MUTED,
          false);
    c.text(
        font,
        materials.containsKey(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)
            ? "Any facing. Fill cauldrons."
            : "Any facing works.",
        112,
        112,
        MUTED,
        false);
  }

  private void habitat(GuiGraphicsExtractor c) {
    if (menu.kind() == 4) {
      c.text(font, "Reclaimed ecosystems", 12, 59, INK, false);
      for (var f : dev.reterra.ecology.Ecology.Family.values()) {
        boolean done = (menu.value(19) & 1 << f.ordinal()) != 0;
        FieldUi.check(c, 12, 72 + f.ordinal() * 12, done);
        c.text(
            font,
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
    c.text(font, font.plainSubstrByWidth(nextGoal(), 276), 12, 57, INK, false);
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
      if (i == 7 && menu.kind() == 3) {
        c.text(font, "No adults needed", px, py, GREEN, false);
        continue;
      }
      int color = values[i] >= targets[i] ? GREEN : MUTED;
      c.text(
          font,
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
    var g = dev.reterra.ecology.Ecology.goals(family(), menu.value(43) != 0);
    return menu.kind() == 3
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
    return dev.reterra.ecology.Ecology.Family.values()[Math.clamp(menu.value(23), 0, 3)];
  }

  private int[] measurements() {
    return new int[] {
      menu.value(10),
      menu.value(11),
      menu.value(12),
      menu.value(13),
      menu.value(14),
      menu.value(15),
      menu.value(16),
      menu.value(29),
      switch (family()) {
        case FOREST -> menu.value(36);
        case SWAMP -> menu.value(37);
        case HIGHLANDS -> menu.value(38);
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
    if (menu.value(45) != 0) {
      if (menu.value(51) != 0) return "Overlaps an existing project. Choose another site.";
      if (menu.value(30) < 9) return "Surveying the site: " + menu.value(30) + "/9 areas.";
      return menu.value(50) > 0
          ? "Rocky ground. Place " + menu.value(50) + " dirt blocks by hand."
          : "Suitable ground. Estimate shows the supplies still needed.";
    }
    if (menu.value(20) != 0)
      return "Reclaimed - recovery area "
          + ((menu.value(34) * 2 + 1) * 16)
          + " x "
          + ((menu.value(34) * 2 + 1) * 16)
          + " blocks.";
    if (menu.value(18) < 128 || (menu.kind() != 3 && menu.value(30) < 9))
      return "Checking the habitat: " + menu.value(30) + "/9 areas.";
    int[] values = measurements();
    int[] goals = targets();
    for (int i = 0; i < goals.length; i++) if (values[i] < goals[i]) return goalHint(i);
    if (menu.kind() == 3) return "Habitat ready. Add a sample to the Chamber.";
    if (menu.value(20) != 0)
      return "Reclaimed! Scarred soil and dead trunks now recover naturally.";
    if (menu.value(31) == 0)
      return "Trial paused at "
          + menu.value(28)
          + "/"
          + trialSeconds()
          + "s. Stay nearby to continue.";
    if (menu.value(6) != 0)
      return "Trial paused at "
          + menu.value(28)
          + "/"
          + trialSeconds()
          + "s. Resume the Core to continue.";
    if (menu.value(9) != 0) return "Trial paused: repair the Core assembly.";
    if (menu.value(47) < 3)
      return "Optional: "
          + ((menu.value(46) & 2) == 0
              ? dev.reterra.ecology.HabitatPlants.careHint(family(), menu.value(52))
              : (menu.value(46) & 4) == 0 ? "feed adults" : "tend crops")
          + " (+15s).";
    return "Stabilizing "
        + menu.value(28)
        + "/"
        + trialSeconds()
        + "s. Keep the habitat healthy.";
  }

  private int trialSeconds() {
    return menu.value(44) > 0 ? menu.value(44) : menu.value(43) != 0 ? 90 : 180;
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
          menu.kind() == 3
              ? "The Chamber can revive the first pair in an empty habitat."
              : "Establish two adult animal types.";
    };
  }

  private String supplyLabel(int i) {
    return switch (i) {
      case 0 -> menu.kind() == 2 ? "Filter" : "Compost";
      case 1 -> "Fuel";
      case 2 -> "Water";
      case 3 ->
          switch (menu.kind()) {
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
    return slot >= 0 && slot < 3 ? menu.value(slot + 1) : 0;
  }

  private String reserveHint(int slot) {
    return switch (slot) {
      case 0 ->
          menu.kind() == 2
              ? "Filter: " + reserve(0) + " cycles left"
              : "Compost: " + reserve(0) + " soil blocks left";
      case 1 -> "Fuel: " + reserve(1) + " actions left";
      default -> "Water: " + reserve(2) + " / 256";
    };
  }

  private String nextStep() {
    var status =
        MachineLogic.Status.values()[
            Math.clamp(menu.value(8), 0, MachineLogic.Status.values().length - 1)];
    return switch (status) {
      case ASSEMBLY -> "Add the missing supports shown in Build.";
      case REGISTER ->
          menu.kind() == 3
              ? "Place inside a registered region."
              : "Register this region to begin.";
      case HABITAT -> nextGoal();
      case SAMPLE -> "Recover a preserved sample from a biological archive.";
      case FOOD -> foodHint();
      case NUTRIENTS -> "Add a nutrient culture.";
      case COMPOST -> menu.kind() == 2 ? "Add a charcoal filter." : "Add organic compost.";
      case FUEL -> "Add coal, charcoal or nutrient culture.";
      case WATER -> "Add a water bucket.";
      case MATERIAL ->
          menu.kind() == 2 ? "Add an empty bucket to fill." : "Add coarse dirt or gravel.";
      case OUTPUT -> "Collect the output to continue.";
      case PAUSED -> "Resume when you are ready.";
      case OVERLAP -> "Choose a site outside the other region.";
      case NO_SPACE -> "Leave two air blocks above nearby grass.";
      case REVIVED -> "Give the pair room and food to breed.";
      case CULTURE -> "Grass culture required.";
      case READY ->
          menu.kind() == 1
              ? "Restoring soil automatically. Plant grass and crops while it works."
              : menu.kind() == 3
                  ? "Ready to incubate. Clear grass beside the chamber."
                  : menu.kind() == 0
                      ? "Turns gravel or coarse dirt into dirt."
                      : menu.value(39) != 0
                          ? "Refills riverbeds within 12 blocks. Restored: " + menu.value(40)
                          : "Fills nearby machine tanks within "
                              + dev.reterra.machine.EcosystemBenefits.pumpReach(menu.value(19))
                              + " blocks.";
      case OBJECTIVES ->
          "Reclaim plains, forest, swamp and highlands: " + menu.value(25) + "/4 ecosystems.";
      case RECLAIMED -> nextGoal();
      case STABILIZING -> nextGoal();
      case INCUBATING -> "Keep the habitat healthy.";
      case AWAKENING -> "Step outside to watch the Heart awaken.";
      case POPULATION -> "Adult population is at capacity (16).";
      case COMPLETE -> "Recovery aura: 272 x 272. Completed regions spread twice as fast.";
      default -> "";
    };
  }

  private void help(GuiGraphicsExtractor c) {
    if (menu.value(45) != 0) {
      int soil = menu.value(48), grass = menu.value(49), work = soil;
      int compost = dev.reterra.machine.EcosystemBenefits.compost(menu.value(19));
      int fuel = dev.reterra.machine.EcosystemBenefits.fuel(menu.value(19), false);
      String[] estimate = {
        "Soil still needed: " + soil + " blocks",
        "Grass still needed: " + grass + " blocks",
        "Approx: " + ((soil + compost - 1) / compost) + " compost",
        "Fuel: "
            + ((work + fuel - 1) / fuel)
            + " coal; water: "
            + ((work + 127) / 128)
            + " buckets",
        "Manual soil placement: " + menu.value(50),
        "Plant grass cultures in several patches."
      };
      for (int i = 0; i < estimate.length; i++)
        c.text(font, estimate[i], 12, 57 + i * 10, INK, false);
      return;
    }
    String text =
        switch (menu.kind()) {
          case 0 ->
              "Processes gravel or coarse dirt into soil. Terrain mode restores a 15 x 15 area."
                  + " Compost: "
                  + dev.reterra.machine.EcosystemBenefits.compost(menu.value(19))
                  + " blocks.";
          case 1 ->
              "Restores soil in a 48 x 48 region. Plant grass cultures yourself. Needs a furnace"
                  + " below.";
          case 2 ->
              "Place beside source water and add filters and fuel. Irrigation fills tanks within "
                  + dev.reterra.machine.EcosystemBenefits.pumpReach(menu.value(19))
                  + " blocks. River mode refills damaged channels.";
          case 3 ->
              "Add a sample, nutrient culture and water. "
                  + foodHint()
                  + " Incubation takes 20 seconds.";
          default ->
              "Four reclaimed ecosystems awaken a permanent 272 x 272 recovery aura. Nature regrows"
                  + " in loaded terrain; completed regions spread twice as fast.";
        };
    FieldUi.wrapped(c, font, Component.literal(text), 12, 59, 276, INK);
  }

  private ItemStack ghost(int slot) {
    return new ItemStack(
        switch (slot) {
          case 0 -> menu.kind() == 2 ? Content.FILTER : Content.COMPOST;
          case 1 -> Items.CHARCOAL;
          case 2 -> Items.WATER_BUCKET;
          case 3 ->
              switch (menu.kind()) {
                case 0 -> Items.GRAVEL;
                case 1 -> Content.CULTURE;
                case 2 -> Items.BUCKET;
                default -> Content.SHEEP_SAMPLE;
              };
          case 4 ->
              MachineLogic.sample(menu.slots.get(3).getItem())
                  ? MachineLogic.food(menu.slots.get(3).getItem())
                  : Items.AIR;
          case 5 -> Content.NUTRIENTS;
          case 6 -> menu.kind() == 2 ? Items.WATER_BUCKET : Items.DIRT;
          default -> Items.BUCKET;
        });
  }

  private Component slotHint(int slot) {
    return Component.literal(
        switch (slot) {
          case 0 ->
              menu.kind() == 2
                  ? "Charcoal filter: 8 pump cycles"
                  : "Compost: "
                      + dev.reterra.machine.EcosystemBenefits.compost(menu.value(19))
                      + " soil blocks";
          case 1 ->
              "Coal/charcoal: "
                  + dev.reterra.machine.EcosystemBenefits.fuel(
                      menu.kind() < 2 ? menu.value(19) : 0, false)
                  + " work";
          case 2 -> "Water bucket: 128 water units";
          case 3 ->
              switch (menu.kind()) {
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
    ItemStack sample = menu.slots.get(3).getItem();
    if (!MachineLogic.sample(sample)) return "Insert an animal sample first.";
    return sample.is(Content.BEE_SAMPLE)
        ? "Add 2 flowers."
        : sample.is(Content.CHICKEN_SAMPLE) ? "Add 4 wheat seeds." : "Add 4 wheat.";
  }

  private void blueprintIcon(GuiGraphicsExtractor c, ItemStack item, int px, int py) {
    FieldUi.push(c, px, py, 0.75f);
    c.item(item, 0, 0);
    FieldUi.pop(c);
  }

  private List<Component> wrap(String text) {
    List<Component> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : text.split(" ")) {
      if (line.length() + word.length() > 46) {
        lines.add(Component.literal(line.toString()));
        line.setLength(0);
      }
      if (!line.isEmpty()) line.append(' ');
      line.append(word);
    }
    if (!line.isEmpty()) lines.add(Component.literal(line.toString()));
    return lines;
  }
}
