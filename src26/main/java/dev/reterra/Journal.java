package dev.reterra;

import net.minecraft.server.level.ServerPlayer;

/** Milestone helper. Progress is explained by discoveries, item tooltips and machine screens. */
public final class Journal {
  private Journal() {}

  public static void grant(ServerPlayer player, String path) {
    var advancement = dev.reterra.compat.WorldAccess.world(player).getServer().getAdvancements().get(Content.id(path));
    if (advancement != null)
      for (String criterion : advancement.value().criteria().keySet())
        player.getAdvancements().award(advancement, criterion);
    discover(player, path);
  }

  public static void discover(ServerPlayer player, String entry) {
    var state = dev.reterra.state.RestorationState.get(dev.reterra.compat.WorldAccess.world(player).getServer().overworld());
    if (state
        .discoveries
        .computeIfAbsent(player.getUUID(), id -> new java.util.HashSet<>())
        .add(entry)) state.setDirty();
  }

  public static dev.reterra.screen.GuidePayload payload(ServerPlayer player) {
    return progress(player, true);
  }

  public static int chapter(java.util.Set<String> notes, boolean reclaimed) {
    int chapter =
        reclaimed || notes.contains("reclaimed")
            ? 5
            : notes.contains("habitat_ready") || notes.contains("natures_return")
                ? 4
                : notes.contains("greener_world") || notes.contains("region_registered")
                    ? 3
                    : notes.contains("wheat") && notes.contains("dirt")
                        ? 2
                        : notes.contains("seeds") || notes.contains("the_last_seed") ? 1 : 0;
    for (int i = 0; i < 6; i++) if (notes.contains("chapter_" + i)) chapter = Math.max(chapter, i);
    return chapter;
  }

  public static void regional(
      dev.reterra.state.RestorationState state,
      dev.reterra.state.RestorationState.Machine core,
      int animalTypes) {
    boolean habitat =
        java.util.Arrays.stream(core.sectors).allMatch(s -> s.surveyed)
            && core.survey(0).habitat(core.family)
            && core.survey(0).trees() >= 1
            && core.survey(0).flowers() >= 1;
    for (var id : core.caretakers) {
      var notes = state.discoveries.computeIfAbsent(id, ignored -> new java.util.HashSet<>());
      boolean changed = notes.add("region_registered");
      if (habitat) changed |= notes.add("habitat_ready");
      if (!core.restoredSpecies.isEmpty()) changed |= notes.add("natures_return");
      if (animalTypes >= 2) changed |= notes.add("two_species");
      if (core.reclaimed) changed |= notes.add("reclaimed");
      if (changed) state.setDirty();
    }
  }

  private static dev.reterra.screen.GuidePayload progress(ServerPlayer player, boolean open) {
    var state = dev.reterra.state.RestorationState.get(dev.reterra.compat.WorldAccess.world(player).getServer().overworld());
    var notes =
        state.discoveries.computeIfAbsent(player.getUUID(), id -> new java.util.HashSet<>());
    var core =
        dev.reterra.machine.RestorationEngine.regionAt(
            dev.reterra.state.RestorationState.get(dev.reterra.compat.WorldAccess.world(player)), player.blockPosition());
    boolean seeds = notes.contains("seeds") || notes.contains("the_last_seed");
    boolean compost = notes.contains("compost"),
        dirt = notes.contains("dirt"),
        wheat = notes.contains("wheat");
    boolean registered = notes.contains("greener_world") || notes.contains("region_registered");
    boolean habitat = notes.contains("habitat_ready") || notes.contains("natures_return");
    int chapter = chapter(notes, !state.completed.isEmpty());
    if (notes.add("chapter_" + chapter)) state.setDirty();
    String objective =
        switch (chapter) {
          case 0 ->
              notes.contains("pouch")
                  ? "Open the Seed Pouch."
                  : state.firstSite == null
                      ? "Look for a farmhouse chimney."
                      : "Follow the farmhouse compass.";
          case 1 -> "Establish your first wheat farm.";
          case 2 -> "Build and register a Restoration Core.";
          case 3 -> "Grow a habitat for the first wildlife.";
          case 4 ->
              notes.contains("two_species")
                  ? "Keep the habitat healthy. Care speeds recovery."
                  : "Bring back two animal species.";
          default ->
              state.heartActivated
                  ? "The Heart is alive. Keep rebuilding."
                  : state.completed.size() == 4
                      ? "Build and awaken the Heart."
                      : "Reclaim each ecosystem: " + state.completed.size() + " / 4.";
        };
    String checklist =
        switch (chapter) {
          case 0, 1 ->
              check(seeds, "Recover preserved seeds")
                  + "\n"
                  + check(compost, "Make organic compost")
                  + "\n"
                  + check(dirt, "Restore fertile dirt")
                  + "\n"
                  + check(wheat, "Harvest your first wheat");
          case 2 ->
              check(notes.contains("culture"), "Make a grass culture")
                  + "\n"
                  + check(notes.contains("reclaimer"), "Craft a Soil Reclaimer")
                  + "\n"
                  + check(registered, "Register a Core region");
          case 3, 4 ->
              check(habitat, "Establish soil, water and varied plants")
                  + "\n"
                  + check(notes.contains("sample"), "Find an animal sample")
                  + "\n"
                  + check(notes.contains("natures_return"), "Incubate your first pair")
                  + (chapter == 4
                      ? "\n" + check(notes.contains("two_species"), "Establish two animal species")
                      : "");
          default ->
              java.util.Arrays.stream(dev.reterra.ecology.Ecology.Family.values())
                  .map(
                      f ->
                          check(
                              state.completed.contains(f),
                              f.name().charAt(0)
                                  + f.name().substring(1).toLowerCase(java.util.Locale.ROOT)
                                  + ": "
                                  + dev.reterra.ecology.Ecology.benefit(f)))
                  .collect(java.util.stream.Collectors.joining("\n"));
        };
    int completed =
        state.completed.stream().mapToInt(f -> 1 << f.ordinal()).reduce(0, (a, b) -> a | b);
    return new dev.reterra.screen.GuidePayload(
        chapter,
        completed,
        objective,
        checklist,
        open,
        chapter == 0 && !notes.contains("pouch") ? state.firstSite : null);
  }

  private static String check(boolean done, String task) {
    return (done ? "[x] " : "[ ] ") + task;
  }

  public static String direction(
      net.minecraft.core.BlockPos from, net.minecraft.core.BlockPos to) {
    int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
    if (Math.abs(dx) < 12 && Math.abs(dz) < 12) return "nearby";
    String ns = Math.abs(dz) > 12 ? dz < 0 ? "north" : "south" : "";
    String ew = Math.abs(dx) > 12 ? dx < 0 ? "west" : "east" : "";
    return ns.isEmpty() ? ew : ew.isEmpty() ? ns : ns + "-" + ew;
  }

  public static void tick(net.minecraft.server.level.ServerLevel world) {
    for (var player : world.players()) {
      for (int slot = 0; slot < 36; slot++) {
        var stack = player.getInventory().getItem(slot);
        if (stack.is(net.minecraft.world.item.Items.WHEAT_SEEDS)) discover(player, "seeds");
        if (stack.is(Content.RECOVERY_BUNDLE)) discover(player, "pouch");
        if (stack.is(Content.COMPOST)) discover(player, "compost");
        if (stack.is(net.minecraft.world.item.Items.DIRT)) discover(player, "dirt");
        if (stack.is(net.minecraft.world.item.Items.WHEAT)) discover(player, "wheat");
        if (stack.is(Content.CULTURE)) discover(player, "culture");
        if (stack.is(Content.RECLAIMER.asItem())) discover(player, "reclaimer");
        if (dev.reterra.machine.MachineLogic.sample(stack)) {
          discover(player, "sample");
          dev.reterra.world.ArchiveSamples.record(
              dev.reterra.state.RestorationState.get(world), stack);
        }
      }
      for (String milestone :
          java.util.List.of(
              "seeds", "compost", "dirt", "wheat", "culture", "reclaimer", "sample")) {
        var advancement =
            dev.reterra.compat.WorldAccess.world(player).getServer().getAdvancements().get(Content.id("notes/" + milestone));
        if (advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone())
          discover(player, milestone);
      }
      var state = dev.reterra.state.RestorationState.get(world);
      for (var entry : state.knownSites.entrySet()) {
        var pos = net.minecraft.core.BlockPos.of(entry.getKey());
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 24 * 24)
          discover(player, dev.reterra.world.SurveyClues.visitedKey(pos));
      }
      var core = dev.reterra.machine.RestorationEngine.regionAt(state, player.blockPosition());
      if (core != null) {
        if (core.caretakers.add(player.getUUID())) state.setDirty();
        regional(state, core, dev.reterra.machine.RestorationEngine.animalSpecies(world, core));
      }
      if (state.firstSite != null
          && !state
              .discoveries
              .getOrDefault(player.getUUID(), java.util.Set.of())
              .contains("site_hint")) {
        discover(player, "site_hint");
      }
      if (state.firstSite != null
          && dev.reterra.machine.RestorationEngine.loaded(world, state.firstSite)
          && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(state.firstSite))
              < 192 * 192
          && !state
              .discoveries
              .getOrDefault(player.getUUID(), java.util.Set.of())
              .contains("seeds"))
        world.sendParticles(
            net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
            state.firstSite.getX() - 3.5,
            state.firstSite.getY() + 7,
            state.firstSite.getZ() - 1.5,
            2,
            .1,
            .1,
            .1,
            .03);
      if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
          player, dev.reterra.screen.GuidePayload.ID))
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
            player, progress(player, false));
    }
  }
}
