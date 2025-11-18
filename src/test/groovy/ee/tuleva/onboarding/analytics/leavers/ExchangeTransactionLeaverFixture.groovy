package ee.tuleva.onboarding.analytics.leavers


import java.time.LocalDate
import java.time.LocalDateTime

class ExchangeTransactionLeaverFixture {

  static ExchangeTransactionLeaver.ExchangeTransactionLeaverBuilder aLeaverWith() {
    ExchangeTransactionLeaver.builder()
        .currentFund("EE3600109435")
        .newFund("EE3600019766")
        .personalCode("38510309513")
        .firstName("John")
        .lastName("Doe")
        .shareAmount(1000.0)
        .sharePercentage(100.0)
        .dateCreated(LocalDate.parse("2021-01-01"))
        .fundOngoingChargesFigure(0.0122)
        .fundNameEstonian("LHV Pensionifond XL")
        .email("john@doe.com")
        .language("ENG")
        .lastEmailSentDate(LocalDateTime.parse("2019-04-30T00:00:00"))
  }

  static ExchangeTransactionLeaver.ExchangeTransactionLeaverBuilder anotherLeaverWith() {
    ExchangeTransactionLeaver.builder()
        .currentFund("EE3600109443")
        .newFund("EE3600019782")
        .personalCode("48510309513")
        .firstName("Jane")
        .lastName("Doe")
        .shareAmount(1000.0)
        .sharePercentage(100.0)
        .dateCreated(LocalDate.parse("2021-01-01"))
        .fundOngoingChargesFigure(0.0057)
        .fundNameEstonian("LHV Pensionifond XS")
        .email("jane@doe.com")
        .language("ENG")
        .lastEmailSentDate(LocalDateTime.parse("2019-04-30T00:00:00"))
  }

  static ExchangeTransactionLeaver.ExchangeTransactionLeaverBuilder thirdLeaverWith() {
    ExchangeTransactionLeaver.builder()
        .currentFund("EE3600109443")
        .newFund("EE3600019774")
        .personalCode("50012319513")
        .firstName("Jack")
        .lastName("Doe")
        .shareAmount(1000.0)
        .sharePercentage(100.0)
        .dateCreated(LocalDate.parse("2021-01-01"))
        .fundOngoingChargesFigure(0.0108)
        .fundNameEstonian("LHV Pensionifond M")
        .email("jack@doe.com")
        .language("ENG")
        .lastEmailSentDate(LocalDateTime.parse("2019-04-30T00:00:00"))
  }

}
