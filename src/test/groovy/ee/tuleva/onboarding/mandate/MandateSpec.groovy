package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.mandate.details.EarlyWithdrawalCancellationMandateDetails
import ee.tuleva.onboarding.mandate.details.TransferCancellationMandateDetails
import ee.tuleva.onboarding.mandate.details.WithdrawalCancellationMandateDetails
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.country.CountryFixture.countryFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.*
import static ee.tuleva.onboarding.pillar.Pillar.SECOND

class MandateSpec extends Specification {

  private Validator validator

  def setup() {
    validator = Validation.buildDefaultValidatorFactory().validator
  }

  def "can group exchanges by source isin, excluding non-positive amounts"() {
    given:
    FundTransferExchange withAmount = FundTransferExchange.builder().sourceFundIsin("isin")
      .amount(BigDecimal.ONE).build()
    FundTransferExchange withoutAmount = FundTransferExchange.builder().sourceFundIsin("isin").build()
    FundTransferExchange withZeroAmount = FundTransferExchange.builder().sourceFundIsin("isin")
      .amount(BigDecimal.ZERO).build()
    when:
    Mandate mandate = Mandate.builder()
      .fundTransferExchanges([withAmount, withoutAmount, withZeroAmount])
      .build()
    then:
    mandate.getFundTransferExchangesBySourceIsin() == ['isin': [withAmount, withoutAmount]]
  }

  def "onUpdate syncs the legacy pillar and payment rate columns from details"() {
    given:
    Mandate mandate = Mandate.builder()
        .pillar(99)
        .paymentRate(null)
        .details(aPaymentRateChangeMandateDetails)
        .metadata([:])
        .build()

    when:
    mandate.onUpdate()

    then:
    // read the raw fields directly: getPillar()/getPaymentRate() recompute from
    // details regardless of whether syncLegacyColumns() ran, so only the stored
    // field values reveal whether onUpdate() actually synced them
    mandate.@pillar == SECOND.toInt()
    mandate.@paymentRate == aPaymentRateChangeMandateDetails.paymentRate.numericValue
  }

  def "getSignedFile throws when the mandate is not signed"() {
    given:
    Mandate mandate = Mandate.builder().metadata([:]).build()

    when:
    mandate.getSignedFile()

    then:
    thrown(IllegalStateException)
  }

  def "getEmail and getPhoneNumber delegate to the user"() {
    given:
    def user = sampleUser().build()
    Mandate mandate = sampleMandate()
    mandate.user = user

    expect:
    mandate.getEmail() == user.email
    mandate.getPhoneNumber() == user.phoneNumber
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

    def submission = mandate.toSubmission()
    then:
    submission.id() == mandate.id
    submission.createdDate() == mandate.createdDate
    submission.address() == mandate.address
    submission.email() == mandate.email
    submission.phoneNumber() == mandate.phoneNumber
    submission.details() instanceof WithdrawalCancellationMandateDetails
  }


  def "can build generic mandate dto for early withdrawal cancellation"() {
    when:
    Mandate mandate = sampleEarlyWithdrawalCancellationMandate()

    def submission = mandate.toSubmission()
    then:
    submission.id() == mandate.id
    submission.createdDate() == mandate.createdDate
    submission.address() == mandate.address
    submission.email() == mandate.email
    submission.phoneNumber() == mandate.phoneNumber
    submission.details() instanceof EarlyWithdrawalCancellationMandateDetails
  }

  def "can build generic mandate dto for transfer cancellation"() {
    when:
    Mandate mandate = sampleTransferCancellationMandate()

    def submission = mandate.toSubmission()
    then:
    submission.id() == mandate.id
    submission.createdDate() == mandate.createdDate
    submission.address() == mandate.address
    submission.email() == mandate.email
    submission.phoneNumber() == mandate.phoneNumber
    submission.details() instanceof TransferCancellationMandateDetails
    ((TransferCancellationMandateDetails) submission.details()).getSourceFundIsinOfTransferToCancel() == mandate.fundTransferExchanges.first.sourceFundIsin
    ((TransferCancellationMandateDetails) submission.details()).getPillar() == SECOND
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
