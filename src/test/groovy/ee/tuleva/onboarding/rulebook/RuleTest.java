package ee.tuleva.onboarding.rulebook;

import static ee.tuleva.onboarding.rulebook.Condition.and;
import static ee.tuleva.onboarding.rulebook.ConditionTest.MANY_HOLDINGS;
import static ee.tuleva.onboarding.rulebook.ConditionTest.MUCH_CASH;
import static ee.tuleva.onboarding.rulebook.Example.applies;
import static ee.tuleva.onboarding.rulebook.Example.doesNotApply;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.rulebook.ConditionTest.Fund;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleTest {

  static final Rule<Fund> REBALANCE =
      new Rule<>(
          "2.6",
          "Suur fond tasakaalustatakse",
          and(MANY_HOLDINGS, MUCH_CASH),
          List.of(
              applies("suur fond palju raha", new Fund(12, 80_000, 2)),
              doesNotApply("väike fond palju raha", new Fund(3, 80_000, 2))));

  @Test
  void aRuleAppliesWhereItsConditionHolds() {
    assertThat(REBALANCE.appliesTo(new Fund(12, 80_000, 0))).isTrue();
    assertThat(REBALANCE.appliesTo(new Fund(12, 0, 0))).isFalse();
  }

  @Test
  void aRuleWithoutAnExampleWhereItAppliesIsRefused() {
    assertThatThrownBy(
            () ->
                new Rule<>(
                    "2.6",
                    "Suur fond tasakaalustatakse",
                    and(MANY_HOLDINGS, MUCH_CASH),
                    List.of(doesNotApply("väike fond", new Fund(3, 0, 0)))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aRuleWithoutAnExampleWhereItDoesNotApplyIsRefused() {
    assertThatThrownBy(
            () ->
                new Rule<>(
                    "2.6",
                    "Suur fond tasakaalustatakse",
                    and(MANY_HOLDINGS, MUCH_CASH),
                    List.of(applies("suur fond", new Fund(12, 80_000, 0)))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aRuleWritesWhenItAppliesItsDataAndEachExampleWithTheFactsThatDecideIt() {
    assertThat(REBALANCE.markdown())
        .isEqualTo(
            List.of(
                "## p 2.6 – Suur fond tasakaalustatakse",
                "Rakendub, kui fondil on vähemalt 10 positsiooni ja fondi raha on vähemalt 50 000"
                    + " eurot.",
                "Andmed: positsioonid on depoopanga aruandest; raha on arvelduskonto jääk.",
                "Näide: suur fond palju raha – rakendub, sest fondil on vähemalt 10 positsiooni ja"
                    + " fondi raha on vähemalt 50 000 eurot.",
                "Näide: väike fond palju raha – ei rakendu, sest fondil on alla 10 positsiooni."));
  }

  @Test
  void anExampleReadsAsTheCodeDecidesItNotAsItIsLabelled() {
    var mislabelled =
        new Rule<>(
            "2.6",
            "Suur fond tasakaalustatakse",
            and(MANY_HOLDINGS, MUCH_CASH),
            List.of(
                applies("väike fond", new Fund(3, 0, 0)),
                doesNotApply("suur fond", new Fund(12, 80_000, 0))));

    assertThat(mislabelled.markdown())
        .contains(
            "Näide: väike fond – ei rakendu, sest fondil on alla 10 positsiooni ja fondi raha on"
                + " alla 50 000 euro.");
    assertThat(mislabelled.mislabelled()).isEqualTo(mislabelled.examples());
  }

  @Test
  void anExampleDecidesAsLabelledWhenTheConditionAgreesWithItsLabel() {
    assertThat(REBALANCE.examples()).allMatch(REBALANCE::decidesAsLabelled);
  }

  @Test
  void aRuleWhoseExamplesDecideAsLabelledHasNoneMislabelled() {
    assertThat(REBALANCE.mislabelled()).isEmpty();
  }
}
