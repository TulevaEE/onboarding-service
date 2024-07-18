package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.mandate.application.ApplicationType
import spock.lang.Specification
import spock.lang.Unroll

import jakarta.validation.ConstraintViolation

import static ee.tuleva.onboarding.mandate.application.ApplicationType.CANCELLATION
import static ee.tuleva.onboarding.mandate.application.ApplicationType.EARLY_WITHDRAWAL
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL
import jakarta.validation.Validation
import jakarta.validation.Validator

class MandateSpec extends Specification {

  private Validator validator

  def setup() {
    validator = Validation.buildDefaultValidatorFactory().validator
  }

  def "can group exchanges by source isin"() {
    given:
    FundTransferExchange withAmount = FundTransferExchange.builder().sourceFundIsin("isin")
      .amount(BigDecimal.ONE).build()
    FundTransferExchange withoutAmount = FundTransferExchange.builder().sourceFundIsin("isin").build()
    when:
    Mandate mandate = Mandate.builder()
      .fundTransferExchanges([withAmount, withoutAmount])
      .build()
    then:
    mandate.getFundTransferExchangesBySourceIsin() == ['isin': [withAmount, withoutAmount]]
  }

  @Unroll
  def "Mandate payment rate validation for rate #rate"() {
    given:
    Mandate mandate = Mandate.builder()
        .pillar(2)
        .metadata(new HashMap<>())
        .paymentRate(rate != null ? new BigDecimal(rate) : null)
        .build()

    when:
    def violations = validator.validate(mandate)
    violations
    Set<ConstraintViolation<Mandate>> allViolations = validator.validate(mandate)
    def paymentRateViolations = allViolations.findAll { it.propertyPath.toString().equals("paymentRate") }
    def isValid = paymentRateViolations.isEmpty()

    then:
    isValid == expectedValidity

    where:
    rate    | expectedValidity
    "2.0"   | true
    "2.1"   | false
    "4.0"   | true
    "6.0"   | true
    null    | true
  }

}
