package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.ConsecutiveBreachTracker.LONGEST_UNCHECKED_GAP_A_STREAK_BRIDGES;
import static ee.tuleva.onboarding.investment.check.tracking.EscalationRule.ESCALATION_NET_TD_THRESHOLD_FALLBACK;
import static ee.tuleva.onboarding.investment.check.tracking.EscalationRule.SISEKORD_4_P_11_8_NOTIFICATION_WORKING_DAY;
import static ee.tuleva.onboarding.investment.check.tracking.TdEscalation.Residual.BREACHED;
import static ee.tuleva.onboarding.investment.check.tracking.TdEscalation.Residual.WITHIN;
import static ee.tuleva.onboarding.investment.check.tracking.TdEscalation.Today.BREACH;
import static ee.tuleva.onboarding.investment.check.tracking.TdEscalation.Today.CLEAN;
import static ee.tuleva.onboarding.rulebook.Condition.and;
import static ee.tuleva.onboarding.rulebook.Condition.or;
import static ee.tuleva.onboarding.rulebook.Example.applies;
import static ee.tuleva.onboarding.rulebook.Example.doesNotApply;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.rulebook.Fact;
import ee.tuleva.onboarding.rulebook.Phrase;
import ee.tuleva.onboarding.rulebook.Rule;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class TdEscalation {

  public enum Today {
    BREACH,
    CLEAN
  }

  public enum Residual {
    BREACHED,
    WITHIN
  }

  public record Streak(int checkedDays, int days, @Nullable BigDecimal netTd, Residual residual) {}

  public record Escalation(
      Today today,
      Streak throughToday,
      @Nullable Streak before,
      int notificationWorkingDay,
      BigDecimal netTdThreshold) {}

  private static final Fact<Escalation> TODAY_BREACHES =
      new Fact<>(
          new Phrase(
              "tänane kontroll ületab lävendit",
              "tänane kontroll ei ületa lävendit",
              "päeva lävend on parameeter TRACKING_BREACH_THRESHOLD andmebaasist; ületamiseks"
                  + " loetakse ka mudelportfelli NAV-jäägi ulatumist lävendini",
              "teavituspäev ja jada liitjälgimisvahe lävend on parameetrid investment_parameter"
                  + " andmebaasist; kui neid ei saa lugeda, on teavituspäev "
                  + SISEKORD_4_P_11_8_NOTIFICATION_WORKING_DAY
                  + ". tööpäev ja lävend "
                  + ESCALATION_NET_TD_THRESHOLD_FALLBACK
                      .movePointRight(2)
                      .toPlainString()
                      .replace('.', ',')
                  + "%",
              "jada sees kuni "
                  + LONGEST_UNCHECKED_GAP_A_STREAK_BRIDGES
                  + " järjestikust kontrollimata tööpäeva loetakse ületanuks"),
          escalation -> escalation.today() == BREACH);

  private static final Fact<Escalation> STREAK_REACHES_THE_NOTIFICATION_DAY =
      new Fact<>(
          new Phrase(
              "tänaseni kestnud ületamiste jada ulatub teavituspäevani",
              "tänaseni kestnud ületamiste jada ei ulatu teavituspäevani"),
          escalation -> escalation.throughToday().days() >= escalation.notificationWorkingDay());

  private static final Fact<Escalation> STREAK_NET_TD_AT_THRESHOLD =
      new Fact<>(
          new Phrase(
              "jada liitjälgimisvahe ulatub lävendini", "jada liitjälgimisvahe jääb alla lävendi"),
          escalation -> {
            var netTd = escalation.throughToday().netTd();
            return netTd != null && netTd.abs().compareTo(escalation.netTdThreshold()) >= 0;
          });

  private static final Fact<Escalation> STREAK_HAD_A_RESIDUAL_BREACH =
      new Fact<>(
          new Phrase(
              "jadas on NAV-jäägi ületamisega päev", "jadas ei ole NAV-jäägi ületamisega päeva"),
          escalation -> escalation.throughToday().residual() == BREACHED);

  private static final Fact<Escalation> EARLIER_STREAK_NOT_YET_NOTIFIED =
      new Fact<>(
          new Phrase(
              "eelmise päevani kestnud jada kontrollitud ületamispäevi on vähem kui"
                  + " teavituspäeva number",
              "eelmise päevani kestnud jada puudub või sellest on juba teavitatud"),
          escalation -> {
            var before = escalation.before();
            return before != null && before.checkedDays() < escalation.notificationWorkingDay();
          });

  private static final Fact<Escalation> TODAY_IS_THE_EARLIER_STREAKS_NOTIFICATION_DAY =
      new Fact<>(
          new Phrase(
              "täna on eelmise päevani kestnud jada teavituspäev või hilisem",
              "eelmise päevani kestnud jada on teavituspäevaks liiga lühike"),
          escalation -> {
            var before = escalation.before();
            return before != null && before.days() >= escalation.notificationWorkingDay() - 1;
          });

  private static final Fact<Escalation> EARLIER_STREAK_NET_TD_AT_THRESHOLD =
      new Fact<>(
          new Phrase(
              "eelmise päevani kestnud jada liitjälgimisvahe ulatub lävendini",
              "eelmise päevani kestnud jada liitjälgimisvahe jääb alla lävendi"),
          escalation -> {
            var before = escalation.before();
            return before != null
                && requireNonNull(before.netTd()).abs().compareTo(escalation.netTdThreshold()) >= 0;
          });

  private static final Fact<Escalation> EARLIER_STREAK_HAD_A_RESIDUAL_BREACH =
      new Fact<>(
          new Phrase(
              "eelmise päevani kestnud jadas on NAV-jäägi ületamisega päev",
              "eelmise päevani kestnud jadas ei ole NAV-jäägi ületamisega päeva"),
          escalation -> {
            var before = escalation.before();
            return before != null && before.residual() == BREACHED;
          });

  private static final int DAY = SISEKORD_4_P_11_8_NOTIFICATION_WORKING_DAY;
  private static final BigDecimal THRESHOLD = ESCALATION_NET_TD_THRESHOLD_FALLBACK;
  private static final Streak NO_STREAK_TODAY = new Streak(0, 0, BigDecimal.ZERO, WITHIN);

  public static final Rule<Escalation> SISEKORD_4_P_11_8 =
      new Rule<>(
          "11.8",
          "Püsivast jälgimisvahest teavitatakse automaatselt",
          or(
              and(
                  TODAY_BREACHES,
                  STREAK_REACHES_THE_NOTIFICATION_DAY,
                  or(STREAK_NET_TD_AT_THRESHOLD, STREAK_HAD_A_RESIDUAL_BREACH)),
              and(
                  EARLIER_STREAK_NOT_YET_NOTIFIED,
                  TODAY_IS_THE_EARLIER_STREAKS_NOTIFICATION_DAY,
                  or(EARLIER_STREAK_NET_TD_AT_THRESHOLD, EARLIER_STREAK_HAD_A_RESIDUAL_BREACH))),
          List.of(
              applies(
                  "neljas järjestikune ületamispäev",
                  new Escalation(
                      BREACH,
                      new Streak(4, 4, new BigDecimal("0.004"), WITHIN),
                      new Streak(3, 3, new BigDecimal("0.003"), WITHIN),
                      DAY,
                      THRESHOLD)),
              applies(
                  "kolm ületamispäeva, neljas päev puhas",
                  new Escalation(
                      CLEAN,
                      NO_STREAK_TODAY,
                      new Streak(3, 3, new BigDecimal("0.003"), WITHIN),
                      DAY,
                      THRESHOLD)),
              applies(
                  "kaks ületamispäeva ja kontrollimata kolmas, neljas päev puhas",
                  new Escalation(
                      CLEAN,
                      NO_STREAK_TODAY,
                      new Streak(2, 3, new BigDecimal("0.003"), WITHIN),
                      DAY,
                      THRESHOLD)),
              applies(
                  "kolm ületamispäeva väikese liitjälgimisvahega, ühel NAV-jäägi ületamine",
                  new Escalation(
                      CLEAN,
                      NO_STREAK_TODAY,
                      new Streak(3, 3, new BigDecimal("0.0002"), BREACHED),
                      DAY,
                      THRESHOLD)),
              doesNotApply(
                  "kolm ületamispäeva, mille liitjälgimisvahe jääb alla lävendi",
                  new Escalation(
                      CLEAN,
                      NO_STREAK_TODAY,
                      new Streak(3, 3, new BigDecimal("0.0005"), WITHIN),
                      DAY,
                      THRESHOLD)),
              doesNotApply(
                  "puhas päev pärast viit ületamispäeva",
                  new Escalation(
                      CLEAN,
                      NO_STREAK_TODAY,
                      new Streak(5, 5, new BigDecimal("0.005"), WITHIN),
                      DAY,
                      THRESHOLD)),
              doesNotApply(
                  "puhas päev pärast kahte ületamispäeva",
                  new Escalation(
                      CLEAN,
                      NO_STREAK_TODAY,
                      new Streak(2, 2, new BigDecimal("0.002"), WITHIN),
                      DAY,
                      THRESHOLD))));

  private TdEscalation() {}
}
