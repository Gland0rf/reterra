package dev.reterra.machine;

/** Supplies pay for actual work, never for ticks, scans, waiting, or empty sectors. */
public final class Balance {
  private Balance() {}

  public static final int CAPACITY = 8192;
  public static final int COMPOST_WORK = 32;
  public static final int COAL_WORK = 128;
  public static final int BIOFUEL_WORK = 256;
  public static final int WATER_WORK = 128;
  public static final int CULTURE_WORK = 128;
  public static final int FILTER_WORK = 8;
  public static final int TANK_CAPACITY = 256;
  public static final int REVIVAL_TICKS = 400;
  public static final int MAX_SPREAD_RADIUS = 4;
}
