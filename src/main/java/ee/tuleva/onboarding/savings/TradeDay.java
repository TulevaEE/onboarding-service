package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.savings.TradeDay.Day.DAY_OFF;
import static ee.tuleva.onboarding.savings.TradeDay.Day.WORKING_DAY;
import static ee.tuleva.onboarding.savings.TradeDay.Trade.NEXT_WORKING_DAY;
import static ee.tuleva.onboarding.savings.TradeDay.Trade.WORKING_DAY_AFTER_NEXT;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.LocalDate;
import java.time.LocalTime;

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

  private TradeDay() {}

  public static Trade trade(Order order) {
    return order.day() == WORKING_DAY && order.placedAt().isBefore(SISEKORD_16_P_1_1_3_ORDER_CUTOFF)
        ? NEXT_WORKING_DAY
        : WORKING_DAY_AFTER_NEXT;
  }

  public static LocalDate dealingDay(LocalDate date, LocalTime time, PublicHolidays holidays) {
    var order = new Order(holidays.isWorkingDay(date) ? WORKING_DAY : DAY_OFF, time);
    return switch (trade(order)) {
      case NEXT_WORKING_DAY -> date;
      case WORKING_DAY_AFTER_NEXT -> holidays.nextWorkingDay(date);
    };
  }
}
