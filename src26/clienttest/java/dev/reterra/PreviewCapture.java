package dev.reterra;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

final class PreviewCapture {
  static void save(Minecraft client, Path path, Runnable complete) {
    java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> save = image -> {
      try (image) {
        image.writeToFile(path);
      } catch (java.io.IOException error) {
        throw new IllegalStateException("Cannot save preview: " + path, error);
      }
      complete.run();
    };
    Screenshot.takeScreenshot(PreviewWorld.renderTarget(client), save);
  }
}
