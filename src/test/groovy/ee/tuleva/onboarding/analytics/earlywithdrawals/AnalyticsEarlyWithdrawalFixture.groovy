package ee.tuleva.onboarding.analytics.earlywithdrawals

import java.time.LocalDate
import java.time.LocalDateTime

public class AnalyticsEarlyWithdrawalFixture {

  static AnalyticsEarlyWithdrawal anEarlyWithdrawal(
      int uniqueId = 0, LocalDateTime lastEmailSent = LocalDateTime.parse("2019-04-30T00:00:00")) {
    return new AnalyticsEarlyWithdrawal(
        "3851030951${uniqueId}",
        "John",
        "Doe",
        "john.doe${uniqueId}@example.com",
        "ENG",
        LocalDate.parse("2023-01-15"),
        "A",
        lastEmailSent
    )
  }
}
