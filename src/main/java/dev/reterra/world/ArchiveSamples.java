package dev.reterra.world;

import dev.reterra.Content;
import dev.reterra.machine.MachineLogic;
import dev.reterra.state.RestorationState;
import java.util.List;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;

/** Preserve one specimen per archive, favoring species not yet recovered by the server. */
public final class ArchiveSamples {
  private ArchiveSamples() {}

  public static List<Item> species() {
    return List.of(
        Content.SHEEP_SAMPLE, Content.COW_SAMPLE, Content.CHICKEN_SAMPLE, Content.BEE_SAMPLE);
  }

  public static void record(RestorationState state, ItemStack sample) {
    if (MachineLogic.sample(sample)
        && state.recoveredSamples.add(Registries.ITEM.getId(sample.getItem()).getPath()))
      state.markDirty();
  }

  public static void diversify(ServerWorld world, Inventory inventory) {
    var state = RestorationState.get(world);
    for (int slot = 0; slot < inventory.size(); slot++) {
      ItemStack found = inventory.getStack(slot);
      if (!MachineLogic.sample(found)) continue;
      if (inventory instanceof net.minecraft.inventory.LootableInventory chest) {
        var surface = chest.getPos().add(-4, 0, -2);
        state.knownSites.put(surface.asLong(), ResearchSites.Kind.BIOLOGICAL_ARCHIVE.name());
      }
      if (state.recoveredSamples.contains(Registries.ITEM.getId(found.getItem()).getPath())) {
        Item unseen =
            species().stream()
                .filter(
                    item -> !state.recoveredSamples.contains(Registries.ITEM.getId(item).getPath()))
                .findFirst()
                .orElse(null);
        if (unseen != null) {
          found = new ItemStack(unseen, found.getCount());
          inventory.setStack(slot, found);
        }
      }
      record(state, found);
      return;
    }
  }
}
