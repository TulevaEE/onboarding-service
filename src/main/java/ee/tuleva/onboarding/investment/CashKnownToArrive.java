package ee.tuleva.onboarding.investment;

import static ee.tuleva.onboarding.rulebook.Condition.and;
import static ee.tuleva.onboarding.rulebook.Example.applies;
import static ee.tuleva.onboarding.rulebook.Example.doesNotApply;

import ee.tuleva.onboarding.rulebook.Fact;
import ee.tuleva.onboarding.rulebook.Phrase;
import ee.tuleva.onboarding.rulebook.Phrases;
import ee.tuleva.onboarding.rulebook.Rule;
import java.time.LocalDate;
import java.util.List;

public final class CashKnownToArrive {

  public record Calculation(LocalDate asOfDate, LocalDate madeOn) {}

  private static final LocalDate SISEKORD_4_P_2_5_3_CASH_KNOWN_TO_ARRIVE_INVESTABLE_FROM =
      LocalDate.of(2027, 1, 1);

  private static final String FROM =
      Phrases.date(SISEKORD_4_P_2_5_3_CASH_KNOWN_TO_ARRIVE_INVESTABLE_FROM);

  private static final Fact<Calculation> DATED_FROM_THE_START =
      new Fact<>(
          new Phrase(
              "arvestuspäev on " + FROM + " või hiljem",
              "arvestuspäev on enne " + FROM,
              "arvestuspäev on päev, mille kohta Fondi vaba raha arvutatakse",
              "laekuv raha on SEB positsiooniaruande nõuete kontol olev Fondi osakute nõue"),
          calculation -> isFromTheStart(calculation.asOfDate()));

  private static final Fact<Calculation> MADE_FROM_THE_START =
      new Fact<>(
          new Phrase(
              "arvutus tehakse " + FROM + " või hiljem",
              "arvutus tehakse enne " + FROM,
              "arvutuse tegemise päev on Tallinna aja järgi"),
          calculation -> isFromTheStart(calculation.madeOn()));

  private static final LocalDate FIRST_DAY =
      SISEKORD_4_P_2_5_3_CASH_KNOWN_TO_ARRIVE_INVESTABLE_FROM;
  private static final LocalDate DAY_BEFORE = FIRST_DAY.minusDays(1);

  public static final Rule<Calculation> SISEKORD_4_P_2_5_3 =
      new Rule<>(
          "2.5.3",
          "Laekuv raha on vaba raha juba enne laekumist",
          and(DATED_FROM_THE_START, MADE_FROM_THE_START),
          List.of(
              applies(
                  "arvutus tehakse " + FROM + " sama päeva kohta",
                  new Calculation(FIRST_DAY, FIRST_DAY)),
              doesNotApply(
                  "arvutus tehakse " + Phrases.date(DAY_BEFORE) + " sama päeva kohta",
                  new Calculation(DAY_BEFORE, DAY_BEFORE)),
              doesNotApply(
                  "arvutus tehakse " + Phrases.date(DAY_BEFORE) + " päeva " + FROM + " kohta",
                  new Calculation(FIRST_DAY, DAY_BEFORE)),
              doesNotApply(
                  "arvutus tehakse " + FROM + " päeva " + Phrases.date(DAY_BEFORE) + " kohta",
                  new Calculation(DAY_BEFORE, FIRST_DAY))));

  private CashKnownToArrive() {}

  private static boolean isFromTheStart(LocalDate day) {
    return !day.isBefore(SISEKORD_4_P_2_5_3_CASH_KNOWN_TO_ARRIVE_INVESTABLE_FROM);
  }
}
