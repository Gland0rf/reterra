package dev.reterra;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Native item and block renders for the release artwork check. */
final class AssetGalleryScreen extends Screen {
  AssetGalleryScreen() {
    super(Component.literal("ReTerra"));
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
    c.fill(0, 0, width, height, 0xFF303A32);
    c.text(font, "ReTerra", 12, 10, 0xFFE4DFC6, false);
    var items =
        new net.minecraft.world.item.Item[] {
          Content.COMPOST,
          Content.CULTURE,
          Content.NUTRIENTS,
          Content.FILTER,
          Content.FRAGMENT,
          Content.SHEEP_SAMPLE,
          Content.COW_SAMPLE,
          Content.CHICKEN_SAMPLE,
          Content.BEE_SAMPLE,
          Content.FIELD_JOURNAL,
          Content.RECOVERY_BUNDLE,
          Content.SCANNER
        };
    int cell = (width - 24) / 5;
    for (int i = 0; i < items.length; i++) {
      var stack = new ItemStack(items[i]);
      int x = 12 + i % 5 * cell, y = 30 + i / 5 * 58;
      dev.reterra.client.FieldUi.push(c, x + (cell - 32) / 2, y, 2);
      c.item(stack, 0, 0);
      dev.reterra.client.FieldUi.pop(c);
      dev.reterra.client.FieldUi.wrapped(c, font, stack.getHoverName(), x + 2, y + 35, cell - 4, 0xFFE4DFC6);
    }
    var blocks =
        new net.minecraft.world.level.block.Block[] {
          Content.RECLAIMER, Content.CORE, Content.WATERWORKS, Content.CHAMBER, Content.HEART
        };
    for (int i = 0; i < blocks.length; i++) {
      int x = 12 + i * cell;
      var stack = new ItemStack(blocks[i]);
      c.item(stack, x + (cell - 16) / 2, 211);
      dev.reterra.client.FieldUi.wrapped(c, font, stack.getHoverName(), x + 2, 232, cell - 4, 0xFFE4DFC6);
    }
  }
}
