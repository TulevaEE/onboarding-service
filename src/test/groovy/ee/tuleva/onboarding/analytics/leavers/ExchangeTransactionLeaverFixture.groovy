package ee.tuleva.onboarding.analytics.leavers


import java.time.LocalDate
import java.time.LocalDateTime

class ExchangeTransactionLeaverFixture {

  static ExchangeTransactionLeaver leaverFixture(LocalDateTime lastEmailSent = LocalDateTime.parse("2019-04-30T00:00:00")) {
    new ExchangeTransactionLeaver(
        "TUK75",
        "LXK75",
        "38510309513",
        "John",
        "Doe",
        1000,
        100,
        LocalDate.parse("2021-01-01"),
        0.0122,
        "LHV Pensionifond XL",
        "john@doe.com",
        "ENG",
        lastEmailSent,
    )
  }

  static ExchangeTransactionLeaver leaverFixture2(LocalDateTime lastEmailSent = LocalDateTime.parse("2019-04-30T00:00:00")) {
    new ExchangeTransactionLeaver(
        "TUK75",
        "LXK00",
        "48510309513",
        "Jane",
        "Doe",
        1000,
        100,
        LocalDate.parse("2021-01-01"),
        0.0057,
        "LHV Pensionifond XS",
        "jane@doe.com",
        "ENG",
        lastEmailSent,
    )
  }

}
