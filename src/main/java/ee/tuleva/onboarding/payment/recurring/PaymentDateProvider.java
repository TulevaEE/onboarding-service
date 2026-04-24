package ee.tuleva.onboarding.payment.recurring;

import static java.time.format.DateTimeFormatter.ofPattern;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentDateProvider {

  private final Clock clock;

  public LocalDate tenthDayOfMonth() {
    return tenthDayOfMonth(LocalDate.now(clock));
  }

  public LocalDate tenthDayOfMonth(LocalDate now) {
    LocalDate date = now.withDayOfMonth(10);

    if (now.getDayOfMonth() > 10) {
      date = date.plusMonths(1);
    }

    return date;
  }

  public static String format(LocalDate date) {
    return date.format(ofPattern("dd.MM.yyyy"));
  }
}
