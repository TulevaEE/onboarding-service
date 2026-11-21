package ee.tuleva.onboarding.investment.transaction;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
sealed interface CashHorizon permits CashHorizon.ThroughTheReportDate, NettingWindow {

  LocalDate lastBookingDay();

  boolean holds(@Nullable UUID entryReference, LocalDate bookingDate);

  record ThroughTheReportDate(LocalDate reportDate) implements CashHorizon {

    @Override
    public LocalDate lastBookingDay() {
      return reportDate;
    }

    @Override
    public boolean holds(@Nullable UUID entryReference, LocalDate bookingDate) {
      return !bookingDate.isAfter(reportDate);
    }
  }
}
