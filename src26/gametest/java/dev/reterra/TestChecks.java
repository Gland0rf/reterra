package dev.reterra;

import net.minecraft.gametest.framework.GameTestHelper;

final class TestChecks {
  static void isTrue(GameTestHelper test, boolean condition, String message) {
    test.assertTrue(condition, net.minecraft.network.chat.Component.literal(message));
  }

  static void isFalse(GameTestHelper test, boolean condition, String message) {
    isTrue(test, !condition, message);
  }
}
