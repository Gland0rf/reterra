package dev.reterra;

import static dev.reterra.ecology.Ecology.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.reterra.ecology.Ecology;
import org.junit.jupiter.api.Test;

class EcologyTest {
  @Test
  void negativeCoordinatesUseMinecraftChunkBoundaries() {
    assertEquals(-1, chunk(-1));
    assertEquals(-1, chunk(-16));
    assertEquals(-2, chunk(-17));
    assertTrue(inRegion(-1, -1, -32, 15));
    assertFalse(inRegion(-1, -1, -33, 0));
  }

  @Test
  void regionsRejectSharedSectorsButAllowAdjacentEdges() {
    assertTrue(overlaps(0, 0, 2, 2));
    assertFalse(overlaps(0, 0, 3, 0));
    assertFalse(overlaps(-3, 0, 0, 0));
  }

  @Test
  void sparseSurveysCannotCompleteRestoration() {
    assertEquals(Stage.BARREN, new Survey(10, 10, 10, 10, 10, 10, 10, 5, 2).stage(Family.PLAINS));
    assertFalse(new Survey(0, 0, 0, 0, 0, 0, 0, 0, 0).habitat());
    assertEquals(0, percent(0, 0));
  }

  @Test
  void completionNeedsRealFoodWaterDiversityAndAnimals() {
    assertEquals(Stage.SOIL, new Survey(256, 200, 0, 4, 4, 4, 3, 3, 2).stage(Family.PLAINS));
    assertEquals(Stage.SOIL, new Survey(256, 200, 120, 0, 4, 4, 3, 3, 2).stage(Family.PLAINS));
    assertEquals(Stage.SOIL, new Survey(256, 200, 120, 4, 0, 4, 3, 3, 2).stage(Family.PLAINS));
    assertEquals(Stage.SOIL, new Survey(256, 200, 120, 4, 4, 4, 3, 2, 2).stage(Family.PLAINS));
    assertEquals(
        Stage.REWILDING, new Survey(256, 200, 120, 8, 12, 4, 6, 5, 0).stage(Family.PLAINS));
    assertEquals(
        Stage.RECLAIMED, new Survey(256, 200, 120, 8, 12, 4, 6, 5, 2).stage(Family.PLAINS));
  }

  @Test
  void highlandsAcceptSparseCanopy() {
    var s = new Survey(256, 200, 120, 8, 12, 2, 6, 5, 2, 0, 0, 12);
    assertEquals(Stage.RECLAIMED, s.stage(Family.HIGHLANDS));
    assertEquals(Stage.GROWTH, s.stage(Family.FOREST));
  }

  @Test
  void firstAnimalHabitatDoesNotRequireExistingAnimalsOrFinalGoals() {
    var first = new Survey(256, 100, 40, 4, 4, 1, 3, 3, 0);
    assertTrue(first.habitat());
    assertNotEquals(Stage.RECLAIMED, first.stage(Family.PLAINS));
  }

  @Test
  void tinyStarterGardenCannotCompleteAWholeRegion() {
    var starter = new Survey(256, 200, 120, 4, 4, 4, 3, 3, 2);
    assertTrue(starter.habitat());
    assertNotEquals(Stage.RECLAIMED, starter.stage(Family.PLAINS));
    var forest = new Survey(256, 200, 120, 8, 12, 48, 6, 5, 2, 2, 0, 0);
    assertEquals(Stage.RECLAIMED, forest.stage(Family.FOREST));
  }

  @Test
  void forestsAndSwampsCanRecoverWithoutBecomingFarms() {
    assertTrue(new Survey(256, 100, 40, 4, 0, 1, 1, 3, 0).habitat(Family.FOREST));
    assertTrue(new Survey(256, 100, 40, 4, 0, 1, 1, 3, 0).habitat(Family.SWAMP));
    assertEquals(
        Stage.RECLAIMED, new Survey(256, 160, 80, 6, 0, 48, 2, 5, 2, 2, 0, 0).stage(Family.FOREST));
    assertEquals(
        Stage.GROWTH, new Survey(256, 160, 80, 6, 0, 48, 2, 5, 2, 1, 0, 0).stage(Family.FOREST));
    assertEquals(
        Stage.RECLAIMED, new Survey(256, 120, 40, 32, 0, 12, 2, 5, 2, 1, 8, 0).stage(Family.SWAMP));
    assertEquals(
        Stage.GROWTH, new Survey(256, 120, 40, 32, 0, 12, 2, 5, 2, 1, 7, 0).stage(Family.SWAMP));
  }

  @Test
  void highlandsNeedLivingSlopesAndPlainsNeedCrops() {
    assertEquals(Stage.GROWTH, new Survey(256, 160, 80, 4, 4, 2, 5, 4, 2).stage(Family.HIGHLANDS));
    assertEquals(
        Stage.RECLAIMED,
        new Survey(256, 160, 80, 4, 4, 2, 5, 4, 2, 1, 0, 12).stage(Family.HIGHLANDS));
    assertNotEquals(
        Stage.RECLAIMED,
        new Survey(256, 200, 120, 8, 0, 48, 6, 5, 2, 2, 8, 12).stage(Family.PLAINS));
  }

  @Test
  void scarsAreStableAndSurviveAcrossNegativeCoordinates() {
    int scars = 0;
    for (int x = -128; x < 128; x++)
      for (int z = -128; z < 128; z++) {
        assertTrue(Ecology.scarHash(x, z) >= 0);
        if (Ecology.isScar(x, z)) scars++;
      }
    assertTrue(scars > 4000 && scars < 6500, "Scar coverage should stay around 1/13 of columns.");
  }
}
