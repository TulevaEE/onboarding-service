package ee.tuleva.onboarding.investment.fees;

import java.math.BigDecimal;
import java.time.LocalDate;

record FeePayment(LocalDate bookedOn, BigDecimal amount) {

  LocalDate month() {
    return bookedOn.withDayOfMonth(1);
  }
}
