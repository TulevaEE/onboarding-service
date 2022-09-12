package ee.tuleva.onboarding.payment;

import org.junit.jupiter.api.Test
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.*;

class PaymentLinkServiceSpec extends Specification {

  Clock clock = Clock.fixed(Instant.parse("2020-11-23T10:00:00Z"), UTC)

  Map<String, PaymentProviderBankConfiguration> paymentProviderBankConfigurations
    = [:]

  PaymentLinkService paymentLinkService

  void setup() {
    paymentProviderBankConfigurations.put(Bank.LHV.getBeanName(), samplePaymentProviderBankConfiguration())
    paymentLinkService = new PaymentLinkService(
        clock,
        paymentProviderBankConfigurations
    )
    paymentLinkService.paymentProviderUrl = "https://sandbox-payments.montonio.com"
  }

  @Test
  void create() {
    when:
    PaymentData paymentData = PaymentData.builder()
        .currency("EUR")
        .amount(BigDecimal.TEN)
        .internalReference("payment id")
        .paymentInformation("for pensionikeskus")
        .userEmail("kasutaja@email.com")
        .bank(Bank.LHV)
        .build()
    String paymentLink = paymentLinkService.create(paymentData)
    then:
    !paymentLink.isBlank()
    paymentLink == "https://sandbox-payments.montonio.com?payment_token=eyJhbGciOiJIUzI1NiJ9.eyJtZXJjaGFudF9yZXR1cm5fdXJsIjoiaHR0cHM6Ly9wZW5zaW9uLnR1bGV2YS5lZSIsImNoZWNrb3V0X2VtYWlsIjoia2FzdXRhamFAZW1haWwuY29tIiwicHJlc2VsZWN0ZWRfYXNwc3AiOiJleGFtcGxlQXNwc3AiLCJtZXJjaGFudF9yZWZlcmVuY2UiOiJwYXltZW50IGlkIiwicGF5bWVudF9pbmZvcm1hdGlvbl91bnN0cnVjdHVyZWQiOiJ0b2RvIGZvciBwZW5zaW9uaWtlc2t1cyIsImFjY2Vzc19rZXkiOiJleGFtcGxlQWNjZXNzS2V5IiwiY3VycmVuY3kiOiJFVVIiLCJleHAiOjE2MDYxMjYyMDAsInByZXNlbGVjdGVkX2xvY2FsZSI6ImV0In0.Vv6zLvlqDFWs5oaB7BX5fQQw9TaHMbcAK6QAgIcoOcc"
  }

  private PaymentProviderBankConfiguration samplePaymentProviderBankConfiguration() {
    PaymentProviderBankConfiguration samplePaymentProviderBankConfiguration = new PaymentProviderBankConfiguration()
    samplePaymentProviderBankConfiguration.accessKey = "exampleAccessKey"
    samplePaymentProviderBankConfiguration.secretKey = "exampleSecretKeyexampleSecretKeyexampleSecretKey"
    samplePaymentProviderBankConfiguration.aspsp = "exampleAspsp"
    return samplePaymentProviderBankConfiguration
  }
}
