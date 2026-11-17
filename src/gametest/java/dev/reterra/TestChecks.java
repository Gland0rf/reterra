package dev.reterra;

import net.minecraft.test.TestContext;

final class TestChecks {
  static void isTrue(TestContext test, boolean condition, String message) {
    //? if <1.21.5 {
    test.assertTrue(condition, message);
    //? } else {
    /*test.assertTrue(condition, net.minecraft.text.Text.literal(message));
    *///? }
  }

  static void isFalse(TestContext test, boolean condition, String message) {
    isTrue(test, !condition, message);
  }
}
