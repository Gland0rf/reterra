package dev.reterra;

import dev.reterra.client.MachineScreen;
import dev.reterra.screen.MachineScreenHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Renders the actual screens in a disposable world after item components have loaded. */
public final class UiRenderCheck implements ClientModInitializer {
  private int ticks, shot, waiting;
  private String lastLoadingScreen = "";
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
    shot = Integer.getInteger("reterra.ui-start-case", 0);
    if (shot < 0 || shot >= CASES.length)
      throw new IllegalArgumentException("UI preview start case must be between 0 and " + (CASES.length - 1));
    System.out.println("UI preview starting at case " + (shot + 1) + "/" + CASES.length);
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          if (!muted) {
            client
                .options
                .getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER)
                .set(0.0);
            client.getSoundManager().stop();
            muted = true;
          }
          PreviewWorld.hide(client);
          client.options.pauseOnLostFocus = false;
          if (ticks == 0) {
            var screen = dev.reterra.client.ClientAccess.screen(client);
            String loadingScreen = screen == null ? "Waiting for world"
                : screen.getClass().getSimpleName() + ": " + screen.getTitle().getString()
                    + " " + screen.children().stream()
                        .filter(child -> child instanceof net.minecraft.client.gui.components.Button)
                        .map(child -> ((net.minecraft.client.gui.components.Button) child).getMessage().getString())
                        .toList();
            if (!loadingScreen.equals(lastLoadingScreen)) {
              System.out.println("UI preview loading: " + loadingScreen);
              lastLoadingScreen = loadingScreen;
            }
            PreviewWorld.dismissLoadPrompt(client);
            if (++waiting > 2400)
              throw new IllegalStateException("UI preview world did not finish loading: " + loadingScreen);
          }
          if (PreviewWorld.loading(client) || client.level == null || client.player == null) return;
          // The loading screen must finish before the first preview replaces it.
          if (ticks == 0 && dev.reterra.client.ClientAccess.screen(client) != null) return;
          if (ticks++ == 0) show(client);
          if (!capturing && ticks > 40 && ticks % 30 == 0) {
            try {
              Path output = Path.of("ui-screenshots");
              Files.createDirectories(output);
              capturing = true;
              PreviewCapture.save(client, output.resolve(
                  "machine-" + CASES[shot][0] + "-page-" + CASES[shot][1] + ".png"), () -> {
                capturing = false;
                System.out.println("UI preview: saved " + (shot + 1) + "/" + CASES.length);
                if (++shot == CASES.length) client.stop();
                else show(client);
              });
            } catch (Exception error) {
              throw new IllegalStateException("UI render check failed", error);
            }
          }
        });
  }

  private void show(Minecraft client) {
    if (CASES[shot][0] == -3) {
      dev.reterra.client.ClientAccess.screen(client, new AssetGalleryScreen());
      return;
    }
    if (CASES[shot][0] == -2) {
      dev.reterra.client.ClientAccess.screen(client, 
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
        var screen = dev.reterra.client.ClientAccess.screen(client);
        var search =
            screen.children().stream()
                .filter(child -> child instanceof net.minecraft.client.gui.components.EditBox)
                .map(child -> (net.minecraft.client.gui.components.EditBox) child)
                .findFirst()
                .orElseThrow();
        search.setValue(CASES[shot][1] == 5 ? "no-such-recipe" : "circuit");
        long results =
            screen.children().stream()
                .filter(
                    child ->
                        child instanceof net.minecraft.client.gui.components.Button button
                            && button.visible
                            && button.getMessage().getString().equals("Control Circuit"))
                .count();
        if (results != (CASES[shot][1] == 5 ? 0 : 1))
          throw new AssertionError("Recipe search did not filter its visible rows.");
        if (CASES[shot][1] == 6) {
          // A circuit ingredient recipe must be browsable and have a working history button.
          search.setValue("revival");
          int pw = Math.min(440, screen.width - 16), ph = Math.min(280, screen.height - 16);
          int gx = (screen.width - pw) / 2 + Math.min(176, pw * 2 / 5) + 28;
          int gy = (screen.height - ph) / 2 + 88;
          PreviewInput.click(screen, gx + 22, gy + 42);
          var previous =
              screen.children().stream()
                  .filter(
                      child ->
                          child instanceof net.minecraft.client.gui.components.Button button
                              && button.getMessage().getString().equals("Previous recipe"))
                  .map(child -> (net.minecraft.client.gui.components.Button) child)
                  .findFirst()
                  .orElseThrow();
          if (!previous.active)
            throw new AssertionError("Ingredient navigation must expose a return path.");
          PreviewInput.press(previous);
          search.setValue("revival");
        }
        if (CASES[shot][1] == 7) {
          search.setValue("");
          PreviewInput.press(screen.children().stream()
              .filter(
                  child ->
                      child instanceof net.minecraft.client.gui.components.Button button
                          && (button.getMessage().getString().equals("Machines")
                              || button.getMessage().getString().equals("Blocks")))
              .map(child -> (net.minecraft.client.gui.components.Button) child)
              .findFirst()
              .orElseThrow());
          long machines =
              screen.children().stream()
                  .filter(
                      child ->
                          child instanceof net.minecraft.client.gui.components.Button button
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
        dev.reterra.client.ClientAccess.screen(client, 
            new Screen(Component.literal("Small guide check")) {
              @Override
              public void extractBackground(GuiGraphicsExtractor c, int mx, int my, float delta) {}

              @Override
              protected void init() {
                guide.init(320, 240);
              }

              @Override
              public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
                guide.extractRenderStateWithTooltipAndSubtitles(c, -100, -100, delta);
              }
            });
      else dev.reterra.client.ClientAccess.screen(client, guide);
      return;
    }
    Inventory inventory = new Inventory(null, new net.minecraft.world.entity.EntityEquipment());
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
    for (int i = 0; i < values.length; i++) handler.setData(i, values[i]);
    handler.setData(44, 180);
    if (CASES[shot][1] == 4) {
      handler.setData(26, 0);
      if (handler.kind() == 3) {
        handler.setData(8, dev.reterra.machine.MachineLogic.Status.INCUBATING.ordinal());
        handler.setData(33, 221);
      } else if (handler.kind() == 1) {
        handler.setData(20, 1);
        handler.setData(34, 2);
        handler.setData(8, dev.reterra.machine.MachineLogic.Status.RECLAIMED.ordinal());
      }
      if (handler.kind() == 2) {
        handler.setData(39, 1);
        handler.setData(40, 42);
      }
      if (handler.kind() == 4) {
        handler.setData(21, 1);
        handler.setData(25, 4);
        handler.setData(8, dev.reterra.machine.MachineLogic.Status.COMPLETE.ordinal());
      }
    }
    if (CASES[shot][1] == 5) {
      handler.setData(26, 1);
      handler.setData(32, 1);
    }
    if (CASES[shot][1] >= 6 && CASES[shot][1] <= 8) {
      handler.setData(26, 2);
      handler.setData(23, CASES[shot][1] - 5);
      handler.setData(36, 1);
      handler.setData(37, 4);
      handler.setData(38, 7);
    }
    if (CASES[shot][1] >= 9) {
      handler.setData(19, 0);
      handler.setData(25, 0);
      handler.setData(26, 2);
      handler.setData(23, 0);
      handler.setData(43, 1);
      handler.setData(44, 90);
      if (CASES[shot][1] == 10) {
        handler.setData(26, 0);
        handler.setData(29, 2);
        handler.setData(28, 55);
        handler.setData(46, 2);
        handler.setData(47, 1);
        handler.setData(8, dev.reterra.machine.MachineLogic.Status.STABILIZING.ordinal());
      }
      if (CASES[shot][1] >= 11) {
        handler.setData(45, 1);
        handler.setData(27, 1);
        handler.setData(48, 807);
        handler.setData(49, 346);
        handler.setData(50, 120);
        handler.setData(10, 0);
        handler.setData(11, 0);
        handler.setData(12, 4);
        for (int property = 13; property <= 17; property++) handler.setData(property, 0);
        handler.setData(18, 2304);
        handler.setData(29, 0);
        if (CASES[shot][1] == 12) handler.setData(26, 3);
      }
    }
    if (handler.kind() == 3 && CASES[shot][1] >= 5) {
      handler.setData(26, 0);
      handler.setData(8, dev.reterra.machine.MachineLogic.Status.FOOD.ordinal());
      var sample =
          switch (CASES[shot][1]) {
            case 5 -> Content.COW_SAMPLE;
            case 6 -> Content.CHICKEN_SAMPLE;
            default -> Content.BEE_SAMPLE;
          };
      if (CASES[shot][1] < 8) handler.getSlot(3).setByPlayer(new net.minecraft.world.item.ItemStack(sample));
      else handler.setData(8, dev.reterra.machine.MachineLogic.Status.SAMPLE.ordinal());
    }
    if (handler.kind() == 4 && CASES[shot][1] == 5) {
      handler.setData(26, 0);
      handler.setData(8, dev.reterra.machine.MachineLogic.Status.AWAKENING.ordinal());
      handler.setData(53, 161);
      handler.setData(25, 4);
    }
    if (handler.kind() == 1 && CASES[shot][1] >= 13) {
      handler.setData(45, 0);
      handler.setData(27, 0);
      handler.setData(26, 0);
      handler.setData(8, dev.reterra.machine.MachineLogic.Status.STABILIZING.ordinal());
      handler.setData(52, CASES[shot][1] == 13 ? 1 : 0);
      handler.setData(23, CASES[shot][1] == 14 ? 2 : 1);
      handler.setData(10, 70);
      handler.setData(11, 50);
      handler.setData(12, 40);
      handler.setData(14, 50);
      handler.setData(15, 5);
      handler.setData(16, 5);
      handler.setData(29, 2);
      handler.setData(36, 2);
      handler.setData(37, 10);
      handler.setData(28, 50);
      if (CASES[shot][1] == 15)
        handler.getSlot(3).setByPlayer(new net.minecraft.world.item.ItemStack(Content.CULTURE, 2));
    }
    MachineScreen screen =
        new MachineScreen(
            handler,
            inventory,
            handler.value(45) != 0
                ? Component.literal("Restoration site survey")
                : Component.translatable("block.reterra." + handler.kindName()));
    dev.reterra.client.ClientAccess.screen(client, 
        new Screen(Component.literal("ReTerra visual check")) {
          @Override
          protected void init() {
            int previewWidth = CASES[shot][1] == 16 ? 320 : width;
            int previewHeight = CASES[shot][1] == 16 ? 240 : height;
            screen.init(previewWidth, previewHeight);
          }

          @Override
          public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xFF101914);
            // Modern Minecraft renders handled-screen backgrounds in this entry point.
            screen.extractRenderStateWithTooltipAndSubtitles(context, -100, -100, delta);
          }
        });
  }
}
