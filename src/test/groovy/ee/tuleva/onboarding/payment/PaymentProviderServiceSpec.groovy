package ee.tuleva.onboarding.payment

import ee.tuleva.onboarding.currency.Currency
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static java.time.ZoneOffset.UTC

class PaymentProviderServiceSpec extends Specification {

  Clock clock = Clock.fixed(Instant.parse("2020-11-23T10:00:00Z"), UTC)

  Map<String, PaymentProviderBankConfiguration> paymentProviderBankConfigurations
    = [:]

  PaymentProviderService paymentLinkService

  void setup() {
    paymentProviderBankConfigurations.put(Bank.LHV.getBeanName(), samplePaymentProviderBankConfiguration())
    paymentLinkService = new PaymentProviderService(
        clock,
        paymentProviderBankConfigurations
    )
    paymentLinkService.paymentProviderUrl = "https://sandbox-payments.montonio.com"
    paymentLinkService.apiUrl = "https://onboarding-service.tuleva.ee/v1"
  }

  void create() {
    given:
    PaymentData paymentData = PaymentData.builder()
        .currency(Currency.EUR)
        .amount(BigDecimal.TEN)
        .internalReference("payment id")
        .description("5566565")
        .reference("232343434")
        .bank(Bank.LHV)
        .firstName("Jordan")
        .lastName("Valdma")
        .build()

    when:
    String paymentLink = paymentLinkService.getPaymentUrl(paymentData)

    then:
    paymentLink == "https://sandbox-payments.montonio.com?payment_token=eyJhbGciOiJIUzI1NiJ9.eyJtZXJjaGFudF9yZXR1cm5fdXJsIjoiaHR0cHM6Ly9vbmJvYXJkaW5nLXNlcnZpY2UudHVsZXZhLmVlL3YxL3BheW1lbnRzL3N1Y2Nlc3MiLCJhbW91bnQiOjEwLCJwYXltZW50X2luZm9ybWF0aW9uX3Vuc3RydWN0dXJlZCI6IjU1NjY1NjUiLCJjaGVja291dF9maXJzdF9uYW1lIjoiSm9yZGFuIiwibWVyY2hhbnRfbm90aWZpY2F0aW9uX3VybCI6Imh0dHBzOi8vb25ib2FyZGluZy1zZXJ2aWNlLnR1bGV2YS5lZS92MS9wYXltZW50cy9ub3RpZmljYXRpb24iLCJwcmVzZWxlY3RlZF9hc3BzcCI6ImV4YW1wbGVBc3BzcCIsIm1lcmNoYW50X3JlZmVyZW5jZSI6InBheW1lbnQgaWQiLCJhY2Nlc3Nfa2V5IjoiZXhhbXBsZUFjY2Vzc0tleSIsInBheW1lbnRfaW5mb3JtYXRpb25fc3RydWN0dXJlZCI6IjIzMjM0MzQzNCIsImN1cnJlbmN5IjoiRVVSIiwiZXhwIjoxNjA2MTI2MjAwLCJwcmVzZWxlY3RlZF9sb2NhbGUiOiJldCIsImNoZWNrb3V0X2xhc3RfbmFtZSI6IlZhbGRtYSJ9.OBnDnKNFkFOiJy9EMA6DwBzT3UKfFe0qDAy4x7k6RMg"
  }

  private PaymentProviderBankConfiguration samplePaymentProviderBankConfiguration() {
    PaymentProviderBankConfiguration samplePaymentProviderBankConfiguration = new PaymentProviderBankConfiguration()
    samplePaymentProviderBankConfiguration.accessKey = "exampleAccessKey"
    samplePaymentProviderBankConfiguration.secretKey = "exampleSecretKeyexampleSecretKeyexampleSecretKey"
    samplePaymentProviderBankConfiguration.aspsp = "exampleAspsp"
    return samplePaymentProviderBankConfiguration
  }
}
