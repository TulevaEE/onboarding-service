package ee.tuleva.onboarding.analytics.leavers


import java.time.LocalDate
import java.time.LocalDateTime

class ExchangeTransactionLeaverFixture {

  static ExchangeTransactionLeaver leaverFixture(LocalDateTime lastEmailSent = LocalDateTime.parse("2019-04-30T00:00:00")) {
    new ExchangeTransactionLeaver(
        "EE3600109435",
        "EE3600019766",
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
        "EE3600109443",
        "EE3600019782",
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

  static ExchangeTransactionLeaver leaverFixture3(LocalDateTime lastEmailSent = LocalDateTime.parse("2019-04-30T00:00:00")) {
    new ExchangeTransactionLeaver(
        "EE3600109443",
        "EE3600019774",
        "50012319513",
        "Jack",
        "Doe",
        1000,
        100,
        LocalDate.parse("2021-01-01"),
        0.0108,
        "LHV Pensionifond M",
        "jack@doe.com",
        "ENG",
        lastEmailSent,
    )
  }

}
