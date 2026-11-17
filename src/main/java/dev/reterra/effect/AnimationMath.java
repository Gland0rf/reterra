package dev.reterra.effect;

/** Continuous choreography shared by the physical Heart crystal and its client-side ornaments. */
public final class AnimationMath {
  private AnimationMath() {}

  public record HeartFrame(double rise, double size, double spin, double orbit) {}

  public static double smooth(double value) {
    double t = Math.clamp(value, 0, 1);
    return t * t * (3 - 2 * t);
  }

  public static HeartFrame heart(double age) {
    double opening = smooth((age - 60) / 100);
    double closing = smooth((age - 250) / 70);
    double breath = 1 + Math.sin(age * .055) * .035;
    return new HeartFrame(
        3.2 * opening * (1 - closing),
        smooth((age - 60) / 55) * (1 - closing) * breath,
        age * .025 + age * age * .000055,
        (.55 + opening * 1.35) * (1 - closing));
  }
}
