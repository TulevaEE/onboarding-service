package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.rulebook.Condition.and;
import static ee.tuleva.onboarding.rulebook.Condition.not;
import static ee.tuleva.onboarding.savings.NavPublication.Shown.PUBLISHED_ON_THE_PREVIOUS_WORKING_DAY;
import static ee.tuleva.onboarding.savings.NavPublication.Shown.PUBLISHED_TODAY;
import static ee.tuleva.onboarding.savings.TradeDay.Day.DAY_OFF;
import static ee.tuleva.onboarding.savings.TradeDay.Day.WORKING_DAY;
import static ee.tuleva.onboarding.savings.TradeDay.SISEKORD_16_P_1_1_3_ORDER_CUTOFF;

import ee.tuleva.onboarding.rulebook.Branch;
import ee.tuleva.onboarding.rulebook.Case;
import ee.tuleva.onboarding.rulebook.Condition;
import ee.tuleva.onboarding.rulebook.Decision;
import ee.tuleva.onboarding.rulebook.Fact;
import ee.tuleva.onboarding.rulebook.Phrase;
import ee.tuleva.onboarding.savings.TradeDay.Day;
import java.time.LocalTime;
import java.util.List;

public final class NavPublication {

  public static final LocalTime SISEKORD_4_P_11_3_3_TKF_NAV_PUBLISHED_AT =
      SISEKORD_16_P_1_1_3_ORDER_CUTOFF;

  public enum Shown {
    PUBLISHED_TODAY,
    PUBLISHED_ON_THE_PREVIOUS_WORKING_DAY
  }

  public record Moment(Day day, LocalTime time) {}

  private static final Fact<Moment> ON_A_WORKING_DAY =
      new Fact<>(
          new Phrase(
              "täna on tööpäev",
              "täna on laupäev, pühapäev või riigipüha",
              "aeg on Eesti aeg",
              "tööpäeval arvutatakse eelmise tööpäeva puhasväärtus",
              "avaldamise aeg on ühtlasi märkimis- ja tagasivõtmiskorralduste cut-off"),
          moment -> moment.day() == WORKING_DAY);

  private static final Fact<Moment> PUBLICATION_TIME_REACHED =
      new Fact<>(
          new Phrase(
              "kell on " + SISEKORD_4_P_11_3_3_TKF_NAV_PUBLISHED_AT + " või hiljem",
              "kell on enne " + SISEKORD_4_P_11_3_3_TKF_NAV_PUBLISHED_AT),
          moment -> !moment.time().isBefore(SISEKORD_4_P_11_3_3_TKF_NAV_PUBLISHED_AT));

  private static final Condition<Moment> PUBLISHED_TODAY_ALREADY =
      and(ON_A_WORKING_DAY, PUBLICATION_TIME_REACHED);

  public static final Decision<Moment, Shown> SISEKORD_4_P_11_3_3 =
      new Decision<>(
          "11.3.3",
          "Täiendava Kogumisfondi puhasväärtus avaldatakse kell "
              + SISEKORD_4_P_11_3_3_TKF_NAV_PUBLISHED_AT,
          List.of(
              new Branch<>(
                  PUBLISHED_TODAY,
                  "näidatakse täna avaldatud puhasväärtust",
                  PUBLISHED_TODAY_ALREADY),
              new Branch<>(
                  PUBLISHED_ON_THE_PREVIOUS_WORKING_DAY,
                  "näidatakse eelmisel tööpäeval avaldatud puhasväärtust",
                  not(PUBLISHED_TODAY_ALREADY))),
          List.of(
              new Case<>(
                  "tööpäeval kell 16:00",
                  new Moment(WORKING_DAY, LocalTime.of(16, 0)),
                  PUBLISHED_TODAY),
              new Case<>(
                  "tööpäeval kell 15:59",
                  new Moment(WORKING_DAY, LocalTime.of(15, 59)),
                  PUBLISHED_ON_THE_PREVIOUS_WORKING_DAY),
              new Case<>(
                  "laupäeval kell 17:00",
                  new Moment(DAY_OFF, LocalTime.of(17, 0)),
                  PUBLISHED_ON_THE_PREVIOUS_WORKING_DAY)));

  private NavPublication() {}
}
