package dev.reterra.client;

import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

/** An ordinary animated compass model, displayed until the first seeds are recovered. */
public final class FarmhouseCompass {
  private static BlockPos target;
  private static ItemStack compass = ItemStack.EMPTY;

  private FarmhouseCompass() {}

  public static void update(BlockPos position) {
    if (java.util.Objects.equals(target, position)) return;
    target = position;
    compass = position == null ? ItemStack.EMPTY : new ItemStack(Items.COMPASS);
    if (position != null)
      compass.set(
          DataComponentTypes.LODESTONE_TRACKER,
          new LodestoneTrackerComponent(
              Optional.of(GlobalPos.create(World.OVERWORLD, position)), false));
  }

  public static void render(DrawContext c, RenderTickCounter ticks) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (target == null
        || client.player == null
        || client.world == null
        || client.options.hudHidden
        || client.currentScreen != null
        || !client.world.getRegistryKey().equals(World.OVERWORLD)) return;
    int left = c.getScaledWindowWidth() / 2 - 55;
    c.fill(left, 7, left + 110, 45, 0xBB18251D);
    FieldUi.push(c, left + 4, 10, 2f);
    c.drawItem(compass, 0, 0);
    FieldUi.pop(c);
    c.drawText(client.textRenderer, "Farmhouse", left + 40, 22, 0xFFE8E3CA, false);
  }
}
