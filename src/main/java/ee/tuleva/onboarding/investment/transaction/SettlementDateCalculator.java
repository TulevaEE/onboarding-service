package ee.tuleva.onboarding.investment.transaction;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class SettlementDateCalculator {

  public LocalDate calculateSettlementDate(LocalDate tradeDate, InstrumentType instrumentType) {
    int businessDays =
        switch (instrumentType) {
          case ETF -> 2;
          case FUND -> 5;
        };

    LocalDate date = tradeDate;
    int count = 0;
    while (count < businessDays) {
      date = date.plusDays(1);
      if (isBusinessDay(date)) {
        count++;
      }
    }
    return date;
  }

  private boolean isBusinessDay(LocalDate date) {
    DayOfWeek day = date.getDayOfWeek();
    return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
  }
}
