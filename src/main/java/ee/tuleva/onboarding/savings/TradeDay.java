package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.rulebook.Condition.and;
import static ee.tuleva.onboarding.rulebook.Condition.not;
import static ee.tuleva.onboarding.savings.TradeDay.Day.DAY_OFF;
import static ee.tuleva.onboarding.savings.TradeDay.Day.WORKING_DAY;
import static ee.tuleva.onboarding.savings.TradeDay.Trade.NEXT_WORKING_DAY;
import static ee.tuleva.onboarding.savings.TradeDay.Trade.WORKING_DAY_AFTER_NEXT;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.rulebook.Branch;
import ee.tuleva.onboarding.rulebook.Case;
import ee.tuleva.onboarding.rulebook.Condition;
import ee.tuleva.onboarding.rulebook.Decision;
import ee.tuleva.onboarding.rulebook.Fact;
import ee.tuleva.onboarding.rulebook.Phrase;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class TradeDay {

  public static final LocalTime SISEKORD_16_P_1_1_3_ORDER_CUTOFF = LocalTime.of(16, 0);

  public enum Day {
    WORKING_DAY,
    DAY_OFF
  }

  public enum Trade {
    NEXT_WORKING_DAY,
    WORKING_DAY_AFTER_NEXT
  }

  public record Order(Day day, LocalTime placedAt) {}

  private static final Fact<Order> ON_A_WORKING_DAY =
      new Fact<>(
          new Phrase(
              "korraldus antakse tööpäeval",
              "korraldus antakse laupäeval, pühapäeval või riigipühal",
              "aeg on Eesti aeg; tööpäev ei ole laupäev, pühapäev ega Eesti riigipüha",
              "korraldust saab tagasi võtta kuni tehingule eelneva tööpäeva kella "
                  + SISEKORD_16_P_1_1_3_ORDER_CUTOFF
                  + "ni"),
          order -> order.day() == WORKING_DAY);

  private static final Fact<Order> BEFORE_THE_CUTOFF =
      new Fact<>(
          new Phrase(
              "korraldus antakse enne kella " + SISEKORD_16_P_1_1_3_ORDER_CUTOFF,
              "korraldus antakse kell " + SISEKORD_16_P_1_1_3_ORDER_CUTOFF + " või hiljem"),
          order -> order.placedAt().isBefore(SISEKORD_16_P_1_1_3_ORDER_CUTOFF));

  private static final Condition<Order> IN_TIME_FOR_THE_NEXT_WORKING_DAY =
      and(ON_A_WORKING_DAY, BEFORE_THE_CUTOFF);

  public static final Decision<Order, Trade> SISEKORD_16_P_1_1_3 =
      new Decision<>(
          "1.1.3",
          "Märkimis- ja tagasivõtmiskorralduse tehingupäev",
          List.of(
              new Branch<>(
                  NEXT_WORKING_DAY,
                  "tehing tehakse järgmisel tööpäeval",
                  IN_TIME_FOR_THE_NEXT_WORKING_DAY),
              new Branch<>(
                  WORKING_DAY_AFTER_NEXT,
                  "tehing tehakse ülejärgmisel tööpäeval",
                  not(IN_TIME_FOR_THE_NEXT_WORKING_DAY))),
          List.of(
              new Case<>(
                  "tööpäeval kell 15:59 antud korraldus",
                  new Order(WORKING_DAY, LocalTime.of(15, 59)),
                  NEXT_WORKING_DAY),
              new Case<>(
                  "tööpäeval kell 16:00 antud korraldus",
                  new Order(WORKING_DAY, LocalTime.of(16, 0)),
                  WORKING_DAY_AFTER_NEXT),
              new Case<>(
                  "laupäeva hommikul antud korraldus",
                  new Order(DAY_OFF, LocalTime.of(10, 0)),
                  WORKING_DAY_AFTER_NEXT)));

  private TradeDay() {}

  public static LocalDate dealingDay(LocalDate date, LocalTime time, PublicHolidays holidays) {
    var order = new Order(holidays.isWorkingDay(date) ? WORKING_DAY : DAY_OFF, time);
    return switch (SISEKORD_16_P_1_1_3.decide(order)) {
      case NEXT_WORKING_DAY -> date;
      case WORKING_DAY_AFTER_NEXT -> holidays.nextWorkingDay(date);
    };
  }
}
