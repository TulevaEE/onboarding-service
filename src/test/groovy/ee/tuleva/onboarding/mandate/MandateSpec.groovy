package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.epis.mandate.GenericMandateDto
import ee.tuleva.onboarding.epis.mandate.details.EarlyWithdrawalCancellationMandateDetails
import ee.tuleva.onboarding.epis.mandate.details.TransferCancellationMandateDetails
import ee.tuleva.onboarding.epis.mandate.details.WithdrawalCancellationMandateDetails
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.country.CountryFixture.countryFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.*
import static ee.tuleva.onboarding.pillar.Pillar.SECOND

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


  def "can build generic mandate dto for withdrawal cancellation"() {
    when:
    Mandate mandate = sampleWithdrawalCancellationMandate()

    GenericMandateDto dto = mandate.getGenericMandateDto()
    then:
    dto.id == mandate.id
    dto.createdDate == mandate.createdDate
    dto.address == mandate.address
    dto.email == mandate.email
    dto.phoneNumber == mandate.phoneNumber
    dto.details instanceof WithdrawalCancellationMandateDetails
  }


  def "can build generic mandate dto for early withdrawal cancellation"() {
    when:
    Mandate mandate = sampleEarlyWithdrawalCancellationMandate()

    GenericMandateDto dto = mandate.getGenericMandateDto()
    then:
    dto.id == mandate.id
    dto.createdDate == mandate.createdDate
    dto.address == mandate.address
    dto.email == mandate.email
    dto.phoneNumber == mandate.phoneNumber
    dto.details instanceof EarlyWithdrawalCancellationMandateDetails
  }

  def "can build generic mandate dto for transfer cancellation"() {
    when:
    Mandate mandate = sampleTransferCancellationMandate()

    GenericMandateDto dto = mandate.getGenericMandateDto()
    then:
    dto.id == mandate.id
    dto.createdDate == mandate.createdDate
    dto.address == mandate.address
    dto.email == mandate.email
    dto.phoneNumber == mandate.phoneNumber
    dto.details instanceof TransferCancellationMandateDetails
    ((TransferCancellationMandateDetails) dto.details).getSourceFundIsinOfTransferToCancel() == mandate.fundTransferExchanges.first.sourceFundIsin
    ((TransferCancellationMandateDetails) dto.details).getPillar() == SECOND
  }

  def "getCountry returns address field"() {
    given:
    def country = countryFixture().build()
    Mandate mandate = Mandate.builder()
        .address(country)
        .pillar(2)
        .metadata([:])
        .build()

    expect:
    mandate.getCountry() == country
  }

}
