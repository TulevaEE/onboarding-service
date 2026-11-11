package ee.tuleva.onboarding.rulebook;

import static ee.tuleva.onboarding.rulebook.Condition.and;
import static ee.tuleva.onboarding.rulebook.Condition.not;
import static ee.tuleva.onboarding.rulebook.Condition.or;
import static ee.tuleva.onboarding.rulebook.Truth.FAILS;
import static ee.tuleva.onboarding.rulebook.Truth.HOLDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionTest {

  record Fund(int holdings, int cash, int age) {}

  static final Fact<Fund> MANY_HOLDINGS =
      new Fact<>(
          new Phrase(
              "fondil on vähemalt 10 positsiooni",
              "fondil on alla 10 positsiooni",
              "positsioonid on depoopanga aruandest"),
          fund -> fund.holdings() >= 10);

  static final Fact<Fund> MUCH_CASH =
      new Fact<>(
          new Phrase(
              "fondi raha on vähemalt 50 000 eurot",
              "fondi raha on alla 50 000 euro",
              "raha on arvelduskonto jääk"),
          fund -> fund.cash() >= 50_000);

  static final Fact<Fund> OLD =
      new Fact<>(
          new Phrase(
              "fond on vähemalt aasta vana",
              "fond on alla aasta vana",
              "positsioonid on depoopanga aruandest"),
          fund -> fund.age() >= 1);

  @Test
  void aFactReadsItsPhraseWhereItHoldsAndWhereItFails() {
    assertThat(MANY_HOLDINGS.text(HOLDS)).isEqualTo("fondil on vähemalt 10 positsiooni");
    assertThat(MANY_HOLDINGS.text(FAILS)).isEqualTo("fondil on alla 10 positsiooni");
  }

  @Test
  void aFactReadsOnAContextAsItDecidesThere() {
    assertThat(MANY_HOLDINGS.textOn(new Fund(3, 0, 0))).isEqualTo("fondil on alla 10 positsiooni");
  }

  @Test
  void aConjunctionReadsWithJa() {
    assertThat(and(MANY_HOLDINGS, MUCH_CASH, OLD).text(HOLDS))
        .isEqualTo(
            "fondil on vähemalt 10 positsiooni, fondi raha on vähemalt 50 000 eurot ja fond on"
                + " vähemalt aasta vana");
  }

  @Test
  void aFailedConjunctionReadsAsADisjunctionOfItsFailures() {
    assertThat(and(MANY_HOLDINGS, MUCH_CASH).text(FAILS))
        .isEqualTo("fondil on alla 10 positsiooni või fondi raha on alla 50 000 euro");
  }

  @Test
  void aFailedDisjunctionReadsAsAConjunctionOfItsFailures() {
    assertThat(or(MANY_HOLDINGS, MUCH_CASH).text(FAILS))
        .isEqualTo("fondil on alla 10 positsiooni ja fondi raha on alla 50 000 euro");
  }

  @Test
  void aJunctionInsideAnotherIsBracketed() {
    assertThat(and(OLD, or(MANY_HOLDINGS, MUCH_CASH)).text(HOLDS))
        .isEqualTo(
            "fond on vähemalt aasta vana ja (fondil on vähemalt 10 positsiooni või fondi raha on"
                + " vähemalt 50 000 eurot)");
  }

  @Test
  void aNegationReadsAsTheOtherPhraseRatherThanAPrefixedEi() {
    assertThat(not(MANY_HOLDINGS).text(HOLDS)).isEqualTo("fondil on alla 10 positsiooni");
    assertThat(not(MANY_HOLDINGS).test(new Fund(3, 0, 0))).isTrue();
  }

  @Test
  void aDoubleNegationIsTheConditionItself() {
    assertThat(not(not(MANY_HOLDINGS))).isEqualTo(MANY_HOLDINGS);
  }

  @Test
  void aConjunctionThatHoldsIsDecidedByAllItsFacts() {
    assertThat(and(MANY_HOLDINGS, MUCH_CASH).facts(new Fund(10, 50_000, 0)))
        .isEqualTo(List.of(MANY_HOLDINGS, MUCH_CASH));
  }

  @Test
  void aConjunctionThatFailsIsDecidedOnlyByTheFactsThatFail() {
    assertThat(and(MANY_HOLDINGS, MUCH_CASH, OLD).facts(new Fund(10, 0, 0)))
        .isEqualTo(List.of(MUCH_CASH, OLD));
  }

  @Test
  void aDisjunctionThatHoldsIsDecidedOnlyByTheFactsThatHold() {
    assertThat(or(MANY_HOLDINGS, MUCH_CASH).facts(new Fund(0, 50_000, 0)))
        .isEqualTo(List.of(MUCH_CASH));
  }

  @Test
  void aJunctionCollectsTheNotesOfItsFactsOnce() {
    assertThat(and(MANY_HOLDINGS, MUCH_CASH, OLD).notes())
        .isEqualTo(List.of("positsioonid on depoopanga aruandest", "raha on arvelduskonto jääk"));
  }

  @Test
  void aJunctionOfFewerThanTwoConditionsIsRefused() {
    assertThatThrownBy(() -> and(MANY_HOLDINGS)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aBlankPhraseIsRefused() {
    assertThatThrownBy(() -> new Phrase(" ", "fondil on alla 10 positsiooni"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
