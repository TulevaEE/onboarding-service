package ee.tuleva.onboarding.savings.fund.pricecheck;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class CheckedBookingDays {

  private final JdbcClient jdbcClient;

  Optional<LocalDate> lastChecked() {
    return jdbcClient
        .sql(
            """
            SELECT last_booking_day FROM issue_redemption_price_check_run
            ORDER BY last_booking_day DESC
            LIMIT 1
            """)
        .query(LocalDate.class)
        .optional();
  }

  void record(PriceCheckRun run, Instant checkedAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO issue_redemption_price_check_run
              (first_booking_day, last_booking_day, deals, reported, checked_at)
            VALUES (:firstBookingDay, :lastBookingDay, :deals, :reported, :checkedAt)
            """)
        .param("firstBookingDay", run.days().first())
        .param("lastBookingDay", run.days().last())
        .param("deals", run.dealings().size())
        .param("reported", run.reported().size())
        .param("checkedAt", Timestamp.from(checkedAt))
        .update();
  }
}
