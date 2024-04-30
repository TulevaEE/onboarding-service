package ee.tuleva.onboarding.analytics


import java.time.LocalDate
import java.time.LocalDateTime

class AnalyticsLeaverFixture {

  static AnalyticsLeaver leaverFixture(LocalDateTime lastEmailSent = LocalDateTime.parse("2019-04-30T00:00:00")) {
    new AnalyticsLeaver(
        "TUK75",
        "LXK75",
        "123456789",
        "John",
        "Doe",
        1000,
        100,
        LocalDate.parse("2021-01-01"),
        0.0113,
        "LHV Pensionifond XL",
        "john@doe.com",
        "ENG",
        30,
        lastEmailSent,
    )
  }

}
