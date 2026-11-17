package dev.reterra;

import java.nio.file.Path;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;

final class PreviewCapture {
  static void save(MinecraftClient client, Path path, Runnable complete) {
    java.util.function.Consumer<net.minecraft.client.texture.NativeImage> save = image -> {
      try (image) {
        image.writeTo(path);
      } catch (java.io.IOException error) {
        throw new IllegalStateException("Cannot save preview: " + path, error);
      }
      complete.run();
    };
    //? if <1.21.5 {
    save.accept(ScreenshotRecorder.takeScreenshot(client.getFramebuffer()));
    //? } else {
    /*ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), save);
    *///? }
  }
}
