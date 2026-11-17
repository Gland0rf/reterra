package dev.reterra.world;

import dev.reterra.Content;
import dev.reterra.machine.MachineLogic;
import dev.reterra.state.RestorationState;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Preserve one specimen per archive, favoring species not yet recovered by the server. */
public final class ArchiveSamples {
  private ArchiveSamples() {}

  public static List<Item> species() {
    return List.of(
        Content.SHEEP_SAMPLE, Content.COW_SAMPLE, Content.CHICKEN_SAMPLE, Content.BEE_SAMPLE);
  }

  public static void record(RestorationState state, ItemStack sample) {
    if (MachineLogic.sample(sample)
        && state.recoveredSamples.add(BuiltInRegistries.ITEM.getKey(sample.getItem()).getPath()))
      state.setDirty();
  }

  public static void diversify(ServerLevel world, Container inventory) {
    var state = RestorationState.get(world);
    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
      ItemStack found = inventory.getItem(slot);
      if (!MachineLogic.sample(found)) continue;
      if (inventory instanceof net.minecraft.world.RandomizableContainer chest) {
        var surface = chest.getBlockPos().offset(-4, 0, -2);
        state.knownSites.put(surface.asLong(), ResearchSites.Kind.BIOLOGICAL_ARCHIVE.name());
      }
      if (state.recoveredSamples.contains(BuiltInRegistries.ITEM.getKey(found.getItem()).getPath())) {
        Item unseen =
            species().stream()
                .filter(
                    item -> !state.recoveredSamples.contains(BuiltInRegistries.ITEM.getKey(item).getPath()))
                .findFirst()
                .orElse(null);
        if (unseen != null) {
          found = new ItemStack(unseen, found.getCount());
          inventory.setItem(slot, found);
        }
      }
      record(state, found);
      return;
    }
  }
}
