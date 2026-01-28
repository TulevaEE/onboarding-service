package ee.tuleva.onboarding.investment.fees;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class FeeMonthResolver {

  public LocalDate resolveFeeMonth(LocalDate calendarDate) {
    return calendarDate.withDayOfMonth(1);
  }
}
