package ee.tuleva.onboarding.investment.fees;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeeMonthResolver {

  private final AumRepository aumRepository;

  public LocalDate resolveFeeMonth(LocalDate calendarDate) {
    LocalDate monthStart = calendarDate.withDayOfMonth(1);
    LocalDate lastBusinessDay = aumRepository.getLastAumDateInMonth(monthStart);

    if (lastBusinessDay != null && calendarDate.isAfter(lastBusinessDay)) {
      return monthStart.plusMonths(1);
    }
    return monthStart;
  }
}
