package dev.reterra;

import dev.reterra.client.MachineScreen;
import dev.reterra.screen.MachineScreenHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Renders the actual screens without a server, a player save, or inventory mutations. */
public final class UiRenderCheck implements ClientModInitializer {
  private int ticks, shot;
  private boolean muted, capturing;
  private static final int[][] CASES = {
    {0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}, {1, 1}, {1, 2}, {3, 2}, {1, 3}, {3, 4}, {1, 4}, {1, 5},
    {-1, 0}, {-1, 1}, {-1, 2}, {-1, 3}, {-1, 4}, {-1, 5}, {1, 6}, {1, 7}, {1, 8}, {2, 4}, {4, 4},
    {-1, 6}, {1, 9}, {1, 10}, {1, 11}, {1, 12}, {4, 2}, {-1, 7}, {-1, 8}, {-2, 0}, {-2, 1}, {-2, 2},
    {-2, 3}, {3, 5}, {3, 6}, {3, 7}, {3, 8}, {4, 5}, {1, 13}, {1, 14}, {1, 15}, {-2, 4}, {-2, 5},
    {-2, 6}, {-2, 7}, {1, 16}, {-1, 9}, {-3, 0}
  };

  @Override
  public void onInitializeClient() {
    if (!Boolean.getBoolean("reterra.ui-preview")) return;
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          if (!muted) {
            client
                .options
                .getSoundVolumeOption(net.minecraft.sound.SoundCategory.MASTER)
                .setValue(0.0);
            client.getSoundManager().stopAll();
            muted = true;
          }
          if (client.getOverlay() != null) return;
          GLFW.glfwHideWindow(client.getWindow().getHandle());
          if (ticks++ == 0) show(client);
          if (!capturing && ticks > 40 && ticks % 30 == 0) {
            try {
              Path output = Path.of("ui-screenshots");
              Files.createDirectories(output);
              capturing = true;
              PreviewCapture.save(client, output.resolve(
                  "machine-" + CASES[shot][0] + "-page-" + CASES[shot][1] + ".png"), () -> {
                capturing = false;
                if (++shot == CASES.length) client.scheduleStop();
                else show(client);
              });
            } catch (Exception error) {
              throw new IllegalStateException("UI render check failed", error);
            }
          }
        });
  }

  private void show(MinecraftClient client) {
    if (CASES[shot][0] == -3) {
      client.setScreen(new AssetGalleryScreen());
      return;
    }
    if (CASES[shot][0] == -2) {
      client.setScreen(
          new dev.reterra.client.RecipeBrowserScreen(
              null,
              new String[] {
                    "soil_reclaimer",
                    "revival_chamber",
                    "compact_soil_coarse_dirt",
                    "compost_from_potatoes"
                  }
                  [Math.min(3, CASES[shot][1])]));
      if (CASES[shot][1] >= 4) {
        var screen = client.currentScreen;
        var search =
            screen.children().stream()
                .filter(child -> child instanceof net.minecraft.client.gui.widget.TextFieldWidget)
                .map(child -> (net.minecraft.client.gui.widget.TextFieldWidget) child)
                .findFirst()
                .orElseThrow();
        search.setText(CASES[shot][1] == 5 ? "no-such-recipe" : "circuit");
        long results =
            screen.children().stream()
                .filter(
                    child ->
                        child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                            && button.visible
                            && button.getMessage().getString().equals("Control Circuit"))
                .count();
        if (results != (CASES[shot][1] == 5 ? 0 : 1))
          throw new AssertionError("Recipe search did not filter its visible rows.");
        if (CASES[shot][1] == 6) {
          // A circuit ingredient recipe must be browsable and have a working history button.
          search.setText("revival");
          int pw = Math.min(440, screen.width - 16), ph = Math.min(280, screen.height - 16);
          int gx = (screen.width - pw) / 2 + Math.min(176, pw * 2 / 5) + 28;
          int gy = (screen.height - ph) / 2 + 88;
          PreviewInput.click(screen, gx + 22, gy + 42);
          var previous =
              screen.children().stream()
                  .filter(
                      child ->
                          child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                              && button.getMessage().getString().equals("Previous recipe"))
                  .map(child -> (net.minecraft.client.gui.widget.ButtonWidget) child)
                  .findFirst()
                  .orElseThrow();
          if (!previous.active)
            throw new AssertionError("Ingredient navigation must expose a return path.");
          PreviewInput.press(previous);
          search.setText("revival");
        }
        if (CASES[shot][1] == 7) {
          search.setText("");
          PreviewInput.press(screen.children().stream()
              .filter(
                  child ->
                      child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                          && (button.getMessage().getString().equals("Machines")
                              || button.getMessage().getString().equals("Blocks")))
              .map(child -> (net.minecraft.client.gui.widget.ButtonWidget) child)
              .findFirst()
              .orElseThrow());
          long machines =
              screen.children().stream()
                  .filter(
                      child ->
                          child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                              && button.visible
                              && button.getHeight() == 22)
                  .count();
          if (machines != 5)
            throw new AssertionError("The machine filter must show exactly the five machines.");
        }
      }
      return;
    }
    if (CASES[shot][0] == -1) {
      if (CASES[shot][1] >= 7 && CASES[shot][1] <= 8)
        dev.reterra.client.FieldGuideScreen.update(
            new dev.reterra.screen.GuidePayload(
                CASES[shot][1] == 7 ? 4 : 5,
                1,
                CASES[shot][1] == 7
                    ? "Establish two animal species, then stabilize their habitat."
                    : "Choose an ecosystem for its research benefit.",
                CASES[shot][1] == 7
                    ? "[x] Establish soil, water and varied plants\n"
                        + "[x] Recover preserved animal tissue\n"
                        + "[x] Incubate your first pair\n"
                        + "[ ] Establish two animal species"
                    : "[x] Plains: richer compost\n"
                        + "[ ] Forest: better fuel yield\n"
                        + "[ ] Swamp: longer pump reach\n"
                        + "[ ] Highlands: faster Core restoration",
                false));
      if (CASES[shot][1] == 6 || CASES[shot][1] == 9)
        dev.reterra.client.FieldGuideScreen.update(
            new dev.reterra.screen.GuidePayload(
                1,
                0,
                "Establish your first wheat farm.",
                "[x] Recover preserved seeds\n"
                    + "[x] Make organic compost\n"
                    + "[ ] Restore fertile dirt\n"
                    + "[ ] Harvest your first wheat",
                false));
      var guide =
          new dev.reterra.client.FieldGuideScreen(
              CASES[shot][1] == 6 || CASES[shot][1] == 9
                  ? 1
                  : CASES[shot][1] == 7 ? 4 : CASES[shot][1] == 8 ? 5 : CASES[shot][1]);
      if (CASES[shot][1] == 9)
        client.setScreen(
            new Screen(Text.literal("Small guide check")) {
              @Override
              public void renderBackground(DrawContext c, int mx, int my, float delta) {}

              @Override
              protected void init() {
                //? if <1.21.11 {
                guide.init(client, 320, 240);
                //? } else {
                /*guide.init(320, 240);
                *///? }
              }

              @Override
              public void render(DrawContext c, int mx, int my, float delta) {
                //? if <1.21.6 {
                guide.render(c, -100, -100, delta);
                //? } else {
                /*guide.renderWithTooltip(c, -100, -100, delta);
                *///? }
              }
            });
      else client.setScreen(guide);
      return;
    }
    //? if <1.21.5 {
    PlayerInventory inventory = new PlayerInventory(null);
    //? } else {
    /*PlayerInventory inventory = new PlayerInventory(null, new net.minecraft.entity.EntityEquipment());
    *///? }
    MachineScreenHandler handler = new MachineScreenHandler(0, inventory);
    int[] values = {
      CASES[shot][0],
      16,
      100,
      96,
      64,
      1,
      0,
      1,
      0,
      0,
      65,
      40,
      6,
      8,
      5,
      5,
      4,
      2,
      1800,
      7,
      0,
      0,
      3,
      0,
      0,
      3,
      CASES[shot][1],
      0,
      45,
      1,
      9,
      1,
      0,
      0,
      1,
      0
    };
    for (int i = 0; i < values.length; i++) handler.setProperty(i, values[i]);
    handler.setProperty(44, 180);
    if (CASES[shot][1] == 4) {
      handler.setProperty(26, 0);
      if (handler.kind() == 3) {
        handler.setProperty(8, dev.reterra.machine.MachineLogic.Status.INCUBATING.ordinal());
        handler.setProperty(33, 221);
      } else if (handler.kind() == 1) {
        handler.setProperty(20, 1);
        handler.setProperty(34, 2);
        handler.setProperty(8, dev.reterra.machine.MachineLogic.Status.RECLAIMED.ordinal());
      }
      if (handler.kind() == 2) {
        handler.setProperty(39, 1);
        handler.setProperty(40, 42);
      }
      if (handler.kind() == 4) {
        handler.setProperty(21, 1);
        handler.setProperty(25, 4);
        handler.setProperty(8, dev.reterra.machine.MachineLogic.Status.COMPLETE.ordinal());
      }
    }
    if (CASES[shot][1] == 5) {
      handler.setProperty(26, 1);
      handler.setProperty(32, 1);
    }
    if (CASES[shot][1] >= 6 && CASES[shot][1] <= 8) {
      handler.setProperty(26, 2);
      handler.setProperty(23, CASES[shot][1] - 5);
      handler.setProperty(36, 1);
      handler.setProperty(37, 4);
      handler.setProperty(38, 7);
    }
    if (CASES[shot][1] >= 9) {
      handler.setProperty(19, 0);
      handler.setProperty(25, 0);
      handler.setProperty(26, 2);
      handler.setProperty(23, 0);
      handler.setProperty(43, 1);
      handler.setProperty(44, 90);
      if (CASES[shot][1] == 10) {
        handler.setProperty(26, 0);
        handler.setProperty(29, 2);
        handler.setProperty(28, 55);
        handler.setProperty(46, 2);
        handler.setProperty(47, 1);
        handler.setProperty(8, dev.reterra.machine.MachineLogic.Status.STABILIZING.ordinal());
      }
      if (CASES[shot][1] >= 11) {
        handler.setProperty(45, 1);
        handler.setProperty(27, 1);
        handler.setProperty(48, 807);
        handler.setProperty(49, 346);
        handler.setProperty(50, 120);
        handler.setProperty(10, 0);
        handler.setProperty(11, 0);
        handler.setProperty(12, 4);
        for (int property = 13; property <= 17; property++) handler.setProperty(property, 0);
        handler.setProperty(18, 2304);
        handler.setProperty(29, 0);
        if (CASES[shot][1] == 12) handler.setProperty(26, 3);
      }
    }
    if (handler.kind() == 3 && CASES[shot][1] >= 5) {
      handler.setProperty(26, 0);
      handler.setProperty(8, dev.reterra.machine.MachineLogic.Status.FOOD.ordinal());
      var sample =
          switch (CASES[shot][1]) {
            case 5 -> Content.COW_SAMPLE;
            case 6 -> Content.CHICKEN_SAMPLE;
            default -> Content.BEE_SAMPLE;
          };
      if (CASES[shot][1] < 8) handler.getSlot(3).setStack(new net.minecraft.item.ItemStack(sample));
      else handler.setProperty(8, dev.reterra.machine.MachineLogic.Status.SAMPLE.ordinal());
    }
    if (handler.kind() == 4 && CASES[shot][1] == 5) {
      handler.setProperty(26, 0);
      handler.setProperty(8, dev.reterra.machine.MachineLogic.Status.AWAKENING.ordinal());
      handler.setProperty(53, 161);
      handler.setProperty(25, 4);
    }
    if (handler.kind() == 1 && CASES[shot][1] >= 13) {
      handler.setProperty(45, 0);
      handler.setProperty(27, 0);
      handler.setProperty(26, 0);
      handler.setProperty(8, dev.reterra.machine.MachineLogic.Status.STABILIZING.ordinal());
      handler.setProperty(52, CASES[shot][1] == 13 ? 1 : 0);
      handler.setProperty(23, CASES[shot][1] == 14 ? 2 : 1);
      handler.setProperty(10, 70);
      handler.setProperty(11, 50);
      handler.setProperty(12, 40);
      handler.setProperty(14, 50);
      handler.setProperty(15, 5);
      handler.setProperty(16, 5);
      handler.setProperty(29, 2);
      handler.setProperty(36, 2);
      handler.setProperty(37, 10);
      handler.setProperty(28, 50);
      if (CASES[shot][1] == 15)
        handler.getSlot(3).setStack(new net.minecraft.item.ItemStack(Content.CULTURE, 2));
    }
    MachineScreen screen =
        new MachineScreen(
            handler,
            inventory,
            handler.value(45) != 0
                ? Text.literal("Restoration site survey")
                : Text.translatable("block.reterra." + handler.kindName()));
    client.setScreen(
        new Screen(Text.literal("ReTerra visual check")) {
          @Override
          protected void init() {
            int previewWidth = CASES[shot][1] == 16 ? 320 : width;
            int previewHeight = CASES[shot][1] == 16 ? 240 : height;
            //? if <1.21.11 {
            screen.init(client, previewWidth, previewHeight);
            //? } else {
            /*screen.init(previewWidth, previewHeight);
            *///? }
          }

          @Override
          public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xFF101914);
            // Modern Minecraft renders handled-screen backgrounds in this entry point.
            //? if <1.21.6 {
            screen.render(context, -100, -100, delta);
            //? } else {
            /*screen.renderWithTooltip(context, -100, -100, delta);
            *///? }
          }
        });
  }
}
