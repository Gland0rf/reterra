package dev.reterra;

import static org.junit.jupiter.api.Assertions.*;

import dev.reterra.ecology.Ecology;
import dev.reterra.machine.EcosystemBenefits;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProgressionTest {
  @Test
  void firstPairStaysInWildlifeChapterAndOnlyReclamationStartsTheFinale() {
    assertEquals(4, Journal.chapter(Set.of("natures_return"), false));
    assertEquals(4, Journal.chapter(Set.of("natures_return", "two_species"), false));
    assertEquals(5, Journal.chapter(Set.of("reclaimed"), false));
    assertEquals(5, Journal.chapter(Set.of(), true));
  }

  @Test
  void permanentMilestonesCannotRegressWhenThePlayerLeavesTheirRegion() {
    assertEquals(3, Journal.chapter(Set.of("greener_world"), false));
    assertEquals(4, Journal.chapter(Set.of("habitat_ready"), false));
    assertEquals(4, Journal.chapter(Set.of("seeds", "chapter_4"), false));
  }

  @Test
  void theFirstSanctuaryNeedsLessWorkWithoutReplacingLaterEcosystemGoals() {
    var survey = new Ecology.Survey(2304, 900, 400, 8, 4, 4, 2, 3, 2);
    assertEquals(Ecology.Stage.RECLAIMED, survey.stage(Ecology.Family.PLAINS, true));
    assertNotEquals(Ecology.Stage.RECLAIMED, survey.stage(Ecology.Family.PLAINS, false));
    assertEquals(90, Ecology.stabilityTarget(true));
    assertEquals(180, Ecology.stabilityTarget(false));
  }

  @Test
  void ecosystemsOfferDifferentUsefulResearchRewards() {
    int plains = 1 << Ecology.Family.PLAINS.ordinal(),
        forest = 1 << Ecology.Family.FOREST.ordinal();
    assertEquals(48, EcosystemBenefits.compost(plains));
    assertEquals(40, EcosystemBenefits.compost(forest));
    assertEquals(192, EcosystemBenefits.fuel(forest, false));
    assertEquals(160, EcosystemBenefits.fuel(plains, false));
    assertEquals(16, EcosystemBenefits.pumpReach(1 << Ecology.Family.SWAMP.ordinal()));
    assertEquals(6, EcosystemBenefits.coreWrites(1 << Ecology.Family.HIGHLANDS.ordinal()));
  }
}
