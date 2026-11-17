package dev.reterra;

import static org.junit.jupiter.api.Assertions.*;

import dev.reterra.machine.Balance;
import org.junit.jupiter.api.Test;

class BalanceTest {
  @Test
  void aFullRegionNeedsFarmingButFitsManageableResourceBudgets() {
    int columns = 48 * 48;
    assertTrue(
        (int) Math.ceil((double) columns / Balance.COMPOST_WORK) >= 32
            && (int) Math.ceil((double) columns / Balance.COMPOST_WORK) <= 96,
        "A full region should reward farming without requiring hundreds of compost.");
    assertTrue(
        (int) Math.ceil(2.0 * columns / Balance.COAL_WORK) >= 16
            && (int) Math.ceil(2.0 * columns / Balance.COAL_WORK) <= 48,
        "Fuel should matter without demanding stacks of coal.");
    assertTrue(
        (int) Math.ceil(2.0 * columns / Balance.WATER_WORK) >= 16
            && (int) Math.ceil(2.0 * columns / Balance.WATER_WORK) <= 48,
        "Water should matter; pumps can handle regional delivery.");
  }
}
