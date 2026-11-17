package dev.reterra.client;

import dev.reterra.Content;
import dev.reterra.effect.AnimationMath;
import dev.reterra.effect.AnimationPayload;
import dev.reterra.effect.AnimationPayload.Kind;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Cosmetic moving parts and short trails. Display parts on newer renderers exist only on the client. */
public final class MachineAnimations {
  private static final int MAX_ACTIVE = 48, MAX_TRAILS = 64;
  private static final Map<Key, Activity> ACTIVE = new LinkedHashMap<>();
  private static final ArrayDeque<Trail> TRAILS = new ArrayDeque<>();
  private static final DustParticleOptions GREEN =
      new DustParticleOptions(0x59E675, .65f);
  private static final DustParticleOptions TEAL =
      new DustParticleOptions(0x4DD9D9, .65f);
  private static final DustParticleOptions GOLD =
      new DustParticleOptions(0xFFD161, .55f);
  private record PartKey(Key activity, int index) {}
  private static final Map<PartKey, net.minecraft.world.entity.Display.BlockDisplay>
      VISUALS = new LinkedHashMap<>();
  private static int nextVisualId = -2_000_000_000;
  private static int remainingParticles;
  private static Object lastWorld;

  private record Key(BlockPos pos, Kind kind) {}

  private record Trail(Vec3 from, Vec3 to, long start, boolean water) {}

  private static final class Activity {
    final AnimationPayload cue;
    final long start;
    long last;
    float previous, progress;

    Activity(AnimationPayload cue, long now) {
      this.cue = cue;
      start = last = now;
      previous = progress = cue.progress();
    }

    double progress(double now) {
      return previous + (progress - previous) * AnimationMath.smooth((now - last) / 5);
    }
  }

  private MachineAnimations() {}

  public static void initialize() {
    ClientPlayNetworking.registerGlobalReceiver(
        AnimationPayload.ID, (cue, context) -> context.client().execute(() -> accept(cue)));
    ClientTickEvents.END_CLIENT_TICK.register(MachineAnimations::tick);
    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
  }

  private static void clear() {
    VISUALS.values().forEach(net.minecraft.world.entity.Entity::discard);
    VISUALS.clear();
    ACTIVE.clear();
    TRAILS.clear();
    lastWorld = null;
  }

  public static void accept(AnimationPayload cue) {
    var client = Minecraft.getInstance();
    if (client.level == null || client.player == null || !Float.isFinite(cue.progress())) return;
    if (lastWorld != client.level) {
      clear();
      lastWorld = client.level;
    }
    if (cue.kind() == Kind.HEART_STOP) {
      ACTIVE
          .keySet()
          .removeIf(
              key ->
                  key.pos.equals(cue.origin())
                      && (key.kind == Kind.HEART || key.kind == Kind.HEART_FINISH));
      return;
    }
    if (client.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(cue.origin())) > 64 * 64) return;
    long now = client.level.getGameTime();
    if (cue.kind() == Kind.HEART_FINISH) ACTIVE.remove(new Key(cue.origin(), Kind.HEART));
    if (cue.kind() == Kind.REVIVED) ACTIVE.remove(new Key(cue.origin(), Kind.REVIVAL));
    var key = new Key(cue.origin(), cue.kind());
    if (cue.kind() == Kind.GROWTH
        || cue.kind() == Kind.REVIVED
        || cue.kind() == Kind.MILESTONE
        || cue.kind() == Kind.HEART_FINISH) ACTIVE.remove(key);
    var activity = ACTIVE.get(key);
    if (activity == null) {
      if (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.remove(ACTIVE.keySet().iterator().next());
      ACTIVE.put(key, new Activity(cue, now));
    } else {
      activity.previous = (float) activity.progress(now);
      activity.progress = cue.progress();
      activity.last = now;
    }
    if ((cue.kind() == Kind.CORE || cue.kind() == Kind.RECLAIMER || cue.kind() == Kind.WATER)
        && !cue.origin().equals(cue.target())) {
      if (TRAILS.size() >= MAX_TRAILS) TRAILS.removeFirst();
      TRAILS.add(
          new Trail(
              net.minecraft.world.phys.Vec3.atCenterOf(cue.origin()).add(0, .8, 0),
              net.minecraft.world.phys.Vec3.atCenterOf(cue.target()).add(0, .65, 0),
              now,
              cue.kind() == Kind.WATER));
    }
  }

  private static int lifetime(Kind kind) {
    return switch (kind) {
      case WATER -> 26;
      case HEART_FINISH -> 48;
      case GROWTH, REVIVED, MILESTONE -> 40;
      default -> 12;
    };
  }

  private static Block machine(Kind kind) {
    return switch (kind) {
      case RECLAIMER -> Content.RECLAIMER;
      case CORE -> Content.CORE;
      case WATER -> Content.WATERWORKS;
      case REVIVAL -> Content.CHAMBER;
      case HEART -> Content.HEART;
      default -> null;
    };
  }

  private static void tick(Minecraft client) {
    if (client.level != lastWorld) clear();
    if (client.level == null || client.player == null || client.isPaused()) return;
    long now = client.level.getGameTime();
    ACTIVE
        .entrySet()
        .removeIf(
            entry -> {
              var a = entry.getValue();
              Block expected = machine(a.cue.kind());
              return now - a.last > lifetime(a.cue.kind())
                  || !client.level.hasChunk(
                      a.cue.origin().getX() >> 4, a.cue.origin().getZ() >> 4)
                  || client.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(a.cue.origin())) > 64 * 64
                  || expected != null && !client.level.getBlockState(a.cue.origin()).is(expected);
            });
    remainingParticles = 128;
    // Give the finale a stable share even with many working machines nearby.
    for (var a : ACTIVE.values())
      if (a.cue.kind() == Kind.HEART || a.cue.kind() == Kind.HEART_FINISH)
        particles(client, a, now);
    for (var a : ACTIVE.values())
      if (a.cue.kind() != Kind.HEART && a.cue.kind() != Kind.HEART_FINISH)
        particles(client, a, now);
    renderLocal(client, now);
    TRAILS.removeIf(trail -> now - trail.start >= 16);
    for (var trail : TRAILS) {
      double t = (now - trail.start) / 16.0;
      for (int i = 0; i < 3; i++) {
        double u = Math.clamp(t - i * .045, 0, 1);
        var point =
            trail
                .from
                .lerp(trail.to, u)
                .add(
                    0,
                    Math.sin(u * Math.PI) * Math.min(3, trail.from.distanceTo(trail.to) * .2),
                    0);
        particle(client, trail.water ? TEAL : GREEN, point, Vec3.ZERO);
      }
      if (now - trail.start == 15)
        for (int i = 0; i < 5; i++)
          particle(
              client,
              trail.water ? ParticleTypes.SPLASH : ParticleTypes.COMPOSTER,
              trail.to.add((i - 2) * .12, 0, 0),
              new Vec3(0, .035, 0));
    }
  }

  private static void particles(Minecraft client, Activity a, long now) {
    if (remainingParticles <= 0) return;
    var origin = a.cue.origin();
    var center = net.minecraft.world.phys.Vec3.atCenterOf(origin).add(0, .6, 0);
    double time = now + Math.floorMod(origin.asLong(), 80), age = now - a.start;
    switch (a.cue.kind()) {
      case RECLAIMER -> {
        if (now % 4 == 0) {
          particle(client, ParticleTypes.POOF, center.add(.42, .25, 0), new Vec3(.015, .035, 0));
          particle(
              client,
              new net.minecraft.core.particles.BlockParticleOption(
                  ParticleTypes.FALLING_DUST, Blocks.DIRT.defaultBlockState()),
              center.add(-.28, .25, 0),
              Vec3.ZERO);
        }
      }
      case CORE -> {
        for (int i = 0; i < 2; i++) {
          double angle = time * .08 + i * Math.PI;
          particle(
              client,
              GOLD,
              center.add(Math.cos(angle) * .65, .25, Math.sin(angle) * .65),
              Vec3.ZERO);
        }
      }
      case WATER -> {
        if (now % 2 == 0)
          particle(
              client,
              ParticleTypes.SPLASH,
              center.add(Math.sin(time * .2) * .35, .5, 0),
              new Vec3(0, .02, 0));
      }
      case REVIVAL -> {
        double progress = a.progress(now);
        for (int strand = 0; strand < 2; strand++)
          for (int i = 0; i < 3; i++) {
            double h = Math.floorMod(now + i * 12, 44) / 44.0;
            double angle = time * .12 + h * 5 + strand * Math.PI;
            particle(
                client,
                strand == 0 ? TEAL : GREEN,
                center.add(
                    Math.cos(angle) * .62, -.65 + h * (1.3 + progress * .5), Math.sin(angle) * .62),
                Vec3.ZERO);
          }
      }
      case HEART -> heartParticles(client, origin, a.progress(now));
      case HEART_FINISH -> {
        wave(client, origin, 27 + age * .38, 24, GREEN, now);
        if (age < 16)
          for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2 + age * .16;
            particle(
                client,
                GOLD,
                center.add(
                    Math.cos(angle) * age * .06, .1 + age * .035, Math.sin(angle) * age * .06),
                new Vec3(0, .015, 0));
          }
      }
      case GROWTH, REVIVED, MILESTONE -> {
        double radius =
            a.cue.kind() == Kind.MILESTONE ? 1 + age * .22 : .3 + Math.min(age, 24) * .11;
        if (age < 28)
          wave(client, origin, radius, 8, a.cue.kind() == Kind.REVIVED ? GOLD : GREEN, now);
        if (now % 3 == 0)
          particle(
              client,
              ParticleTypes.HAPPY_VILLAGER,
              center.add(Math.cos(time) * radius * .6, .2, Math.sin(time) * radius * .6),
              Vec3.ZERO);
      }
      default -> {}
    }
  }

  private static void heartParticles(Minecraft client, BlockPos origin, double age) {
    var center = net.minecraft.world.phys.Vec3.atCenterOf(origin).add(0, .5, 0);
    var frame = AnimationMath.heart(age);
    if (age < 80) {
      double radius = 6 * (1 - AnimationMath.smooth(age / 85)) + .4;
      for (int arm = 0; arm < 6; arm++)
        for (int tail = 0; tail < 3; tail++) {
          double angle = arm * Math.PI / 3 + age * .012 + tail * .035;
          double r = radius + tail * .18;
          double x = center.x + Math.cos(angle) * r, z = center.z + Math.sin(angle) * r;
          double ground = groundHeight(client, x, z, center.y);
          particle(
              client,
              tail == 0 ? GOLD : GREEN,
              new Vec3(x, ground + .12, z),
              new Vec3(0, .008, 0));
        }
    }
    if (age >= 60 && age < 310) {
      for (int strand = 0; strand < 2; strand++)
        for (int i = 0; i < 12; i++) {
          double h = i / 11.0;
          double angle = age * .055 - h * 5.5 + strand * Math.PI;
          double radius = (.38 + Math.sin(h * Math.PI) * .8) * frame.size();
          particle(
              client,
              strand == 0 ? GREEN : TEAL,
              center.add(
                  Math.cos(angle) * radius, h * (frame.rise() + 1.2), Math.sin(angle) * radius),
              Vec3.ZERO);
        }
      for (int i = 0; i < 4; i++) {
        double angle = -frame.spin() * .7 + i * Math.PI / 2;
        var point =
            center.add(
                Math.cos(angle) * frame.orbit(),
                .5 + frame.rise() + Math.sin(angle * 2) * .35,
                Math.sin(angle) * frame.orbit());
        particle(client, GOLD, point, Vec3.ZERO);
      }
      if (age >= 170 && age < 260) {
        double height = (age - 170) / 90 * 11;
        for (int i = 0; i < 6; i++)
          particle(
              client,
              i % 2 == 0 ? TEAL : GOLD,
              center.add(
                  Math.sin(age * .1 + i) * .1,
                  frame.rise() + 1 + height * i / 6,
                  Math.cos(age * .1 + i) * .1),
              new Vec3(0, .025, 0));
      }
    }
    if (age >= 260) wave(client, origin, (age - 260) * .45, 28, GREEN, (long) age);
  }

  private static void wave(
      Minecraft client,
      BlockPos origin,
      double radius,
      int points,
      ParticleOptions effect,
      long time) {
    for (int i = 0; i < points && remainingParticles > 0; i++) {
      double angle = (i + (time % 3) / 3.0) * Math.PI * 2 / points;
      double x = origin.getX() + .5 + Math.cos(angle) * radius,
          z = origin.getZ() + .5 + Math.sin(angle) * radius;
      double y = groundHeight(client, x, z, Double.NaN);
      if (Double.isFinite(y))
        particle(client, effect, new Vec3(x, y + .15, z), new Vec3(0, .015, 0));
    }
  }

  private static double groundHeight(Minecraft client, double x, double z, double fallback) {
    int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
    if (!client.level.hasChunk(bx >> 4, bz >> 4)) return fallback;
    int top = client.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
    for (int depth = 0; depth < 64 && top - depth >= client.level.getMinY(); depth++) {
      var p = new BlockPos(bx, top - depth, bz);
      var block = client.level.getBlockState(p);
      if (block.isAir()
          || block.is(BlockTags.LOGS)
          || block.is(BlockTags.LEAVES)
          || block.getBlock() instanceof VegetationBlock
          || block.is(Blocks.MANGROVE_ROOTS)
          || block.is(Blocks.MUDDY_MANGROVE_ROOTS)) continue;
      return p.getY() + 1;
    }
    return fallback;
  }

  private static void particle(
      Minecraft client, ParticleOptions effect, Vec3 p, Vec3 velocity) {
    if (remainingParticles-- <= 0
        || !client.level.hasChunk(((int) Math.floor(p.x)) >> 4, ((int) Math.floor(p.z)) >> 4))
      return;
    client.level.addParticle(effect, p.x, p.y, p.z, velocity.x, velocity.y, velocity.z);
  }

  @FunctionalInterface
  private interface PartSink {
    void draw(Block block, double x, double y, double z,
        double sx, double sy, double sz, double yaw, double roll);
  }

  private static void parts(Activity a, double now, PartSink sink) {
    var pos = a.cue.origin();
    double fade = AnimationMath.smooth((now - a.start + 1) / 5)
        * (1 - AnimationMath.smooth((now - a.last - lifetime(a.cue.kind()) + 5) / 5));
    double time = now + Math.floorMod(pos.asLong(), 80);
      switch (a.cue.kind()) {
        case RECLAIMER -> {
          double lift = 1.12 + (.5 + Math.cos(time * .32) * .5) * .4 * fade;
          sink.draw(Blocks.IRON_BLOCK, 0, lift, 0, .68 * fade, .08 * fade, .68 * fade, 0, 0);
          sink.draw(Blocks.POLISHED_ANDESITE,
              0,
              lift + .21,
              0,
              .13 * fade,
              .4 * fade,
              .13 * fade,
              0,
              0);
        }
        case CORE -> {
          for (int i = 0; i < 4; i++) {
            double angle = time * .07 + i * Math.PI / 2;
            sink.draw(dev.reterra.compat.GameAccess.COPPER_BLOCK,
                Math.cos(angle) * .67,
                1.25 + Math.sin(time * .05) * .08,
                Math.sin(angle) * .67,
                .2 * fade,
                .09 * fade,
                .38 * fade,
                -angle,
                .25);
          }
        }
        case WATER -> {
          for (int i = 0; i < 3; i++)
            sink.draw(dev.reterra.compat.GameAccess.COPPER_BLOCK,
                0,
                1.52,
                0,
                .1 * fade,
                .85 * fade,
                .18 * fade,
                0,
                time * .12 + i * Math.PI / 3);
          sink.draw(Blocks.IRON_BLOCK, 0, 1.52, 0, .16 * fade, .16 * fade, .4 * fade, 0, 0);
        }
        case REVIVAL -> {
          double size = (.17 + a.progress(now) * .25) * fade;
          sink.draw(Blocks.AMETHYST_BLOCK,
              0,
              1.38 + Math.sin(time * .06) * .12,
              0,
              size,
              size * 1.8,
              size,
              time * .06,
              Math.PI / 4);
        }
        case HEART -> {
          var frame = AnimationMath.heart(a.progress(now));
          for (int i = 0; i < 4; i++) {
            double angle = -frame.spin() * .7 + i * Math.PI / 2;
            double size = .23 * frame.size() * fade;
            sink.draw(i % 2 == 0 ? Blocks.AMETHYST_BLOCK : Blocks.EMERALD_BLOCK,
                Math.cos(angle) * frame.orbit(),
                1.5 + frame.rise() + Math.sin(angle * 2) * .35,
                Math.sin(angle) * frame.orbit(),
                size * .7,
                size * 1.7,
                size * .7,
                angle,
                Math.PI / 4);
          }
        }
        default -> {}
      }
  }
  private static void renderLocal(Minecraft client, long now) {
    var used = new java.util.HashSet<PartKey>();
    for (var a : ACTIVE.values()) {
      if (client.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(a.cue.origin())) > 48 * 48) continue;
      var key = new Key(a.cue.origin(), a.cue.kind());
      int[] index = {0};
      parts(a, now, (block, x, y, z, sx, sy, sz, yaw, roll) -> {
        var partKey = new PartKey(key, index[0]++);
        if (sx <= .001 || sy <= .001 || sz <= .001) return;
        used.add(partKey);
        var display = VISUALS.get(partKey);
        if (display == null || display.isRemoved()) {
          display = dev.reterra.compat.GameAccess.BLOCK_DISPLAY.create(
              client.level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
          if (display == null) return;
          while (client.level.getEntity(nextVisualId) != null) nextVisualId--;
          display.setId(nextVisualId--);
          display.setPos(key.pos.getX() + .5, key.pos.getY(), key.pos.getZ() + .5);
          var access = (dev.reterra.mixin.DisplayAnimationAccess) display;
          access.reterra$width(4);
          access.reterra$height(5);
          access.reterra$brightness(new net.minecraft.util.Brightness(15, 15));
          client.level.addEntity(display);
          VISUALS.put(partKey, display);
        }
        ((dev.reterra.mixin.CrystalDisplayAccess) display).reterra$block(block.defaultBlockState());
        var access = (dev.reterra.mixin.DisplayAnimationAccess) display;
        access.reterra$duration(1);
        access.reterra$transform(new com.mojang.math.Transformation(
            new org.joml.Matrix4f().translation((float) x, (float) y, (float) z)
                .rotateY((float) yaw).rotateZ((float) roll)
                .scale((float) sx, (float) sy, (float) sz).translate(-.5f, -.5f, -.5f)));
        access.reterra$start(0);
      });
    }
    VISUALS.entrySet().removeIf(entry -> {
      if (used.contains(entry.getKey())) return false;
      entry.getValue().discard();
      return true;
    });
  }
}
