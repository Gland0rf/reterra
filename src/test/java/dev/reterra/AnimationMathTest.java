package dev.reterra;

import static org.junit.jupiter.api.Assertions.*;

import dev.reterra.effect.AnimationMath;
import org.junit.jupiter.api.Test;

class AnimationMathTest {
  @Test
  void heartRisesAndSettlesWithoutJumpsOrNegativeScales() {
    var previous = AnimationMath.heart(0);
    for (int tick = 1; tick <= 320; tick++) {
      var frame = AnimationMath.heart(tick);
      assertTrue(Double.isFinite(frame.spin()));
      assertTrue(frame.size() >= 0 && frame.size() <= 1.04);
      assertTrue(frame.rise() >= 0 && frame.rise() <= 3.2);
      assertTrue(frame.orbit() >= 0 && frame.orbit() <= 1.901);
      assertTrue(
          Math.abs(frame.rise() - previous.rise()) < .08,
          "The crystal must ease into each phase instead of jumping.");
      previous = frame;
    }
    assertEquals(0, AnimationMath.heart(60).size());
    assertEquals(3.2, AnimationMath.heart(200).rise());
    assertEquals(0, AnimationMath.heart(320).size());
    assertEquals(0, AnimationMath.heart(320).rise());
    assertEquals(0, AnimationMath.heart(320).orbit());
  }
}
