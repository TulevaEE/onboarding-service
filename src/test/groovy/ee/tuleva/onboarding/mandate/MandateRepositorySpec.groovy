package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.epis.mandate.details.*
import ee.tuleva.onboarding.user.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.country.CountryFixture.countryFixture
import static ee.tuleva.onboarding.epis.mandate.details.BankAccountDetails.BankAccountType.ESTONIAN
import static ee.tuleva.onboarding.epis.mandate.details.PaymentRateChangeMandateDetails.PaymentRate.SIX
import static ee.tuleva.onboarding.mandate.MandateFixture.*
import static ee.tuleva.onboarding.mandate.MandateType.*
import static ee.tuleva.onboarding.pillar.Pillar.SECOND

@DataJpaTest
class MandateRepositorySpec extends Specification {

  @Autowired
  private TestEntityManager entityManager

  @Autowired
  private MandateRepository repository

  private User savedUser

  def setup() {
    savedUser = User.builder()
      .firstName("Erko")
      .lastName("Risthein")
      .personalCode("38501010002")
      .email("erko@risthein.ee")
      .phoneNumber("5555555")
      .createdDate(Instant.parse("2017-01-31T10:06:01Z"))
      .updatedDate(Instant.parse("2017-01-31T10:06:01Z"))
      .active(true)
      .build()
    entityManager.persistAndFlush(savedUser)
  }

  def "persisting and findByIdAndUserId() works"() {
    given:
    def address = countryFixture().build()
    def metadata = ["conversion": true]

    def savedMandate = Mandate.builder()
      .user(savedUser)
      .futureContributionFundIsin("isin")
      .pillar(2)
      .details(new TransferCancellationMandateDetails("EE_TEST_ISIN", SECOND))
      .address(address)
      .metadata(metadata)
      .build()
    def fundTransferExchange = FundTransferExchange.builder()
      .sourceFundIsin("AE123232331")
      .targetFundIsin(null)
      .mandate(savedMandate)
      .build()
    savedMandate.fundTransferExchanges = [fundTransferExchange]

    entityManager.persistAndFlush(savedMandate)
    entityManager.clear()

    when:
    def mandate = repository.findByIdAndUserId(savedMandate.id, savedUser.id)
    def details = (TransferCancellationMandateDetails) mandate.details

    then:
    mandate.user.id == savedUser.id
    mandate.futureContributionFundIsin == Optional.of("isin")
    mandate.fundTransferExchanges.first().sourceFundIsin == "AE123232331"
    mandate.address == address
    mandate.metadata == metadata
    details.mandateType == TRANSFER_CANCELLATION
    details.pillar == SECOND
    details.sourceFundIsinOfTransferToCancel == "EE_TEST_ISIN"
  }

  def "PaymentRateChangeMandateDetails round-trips through JSONB"() {
    given:
    def mandate = persistMandateWithDetails(aPaymentRateChangeMandateDetails)
    entityManager.clear()

    when:
    def loaded = repository.findByIdAndUserId(mandate.id, savedUser.id)
    def details = (PaymentRateChangeMandateDetails) loaded.details

    then:
    details.mandateType == PAYMENT_RATE_CHANGE
    details.paymentRate == SIX
    details.paymentRate.numericValue == 6
  }

  def "PartialWithdrawalMandateDetails round-trips through JSONB"() {
    given:
    def mandate = persistMandateWithDetails(aPartialWithdrawalMandateDetails)
    entityManager.clear()

    when:
    def loaded = repository.findByIdAndUserId(mandate.id, savedUser.id)
    def details = (PartialWithdrawalMandateDetails) loaded.details

    then:
    details.mandateType == PARTIAL_WITHDRAWAL
    details.pillar == SECOND
    details.bankAccountDetails == new BankAccountDetails(ESTONIAN, "EE591254471322749514")
    details.fundWithdrawalAmounts.size() == 2
    details.fundWithdrawalAmounts[0].isin() == "EE3600109435"
    details.fundWithdrawalAmounts[0].percentage() == 10
    details.fundWithdrawalAmounts[0].units() == BigDecimal.valueOf(20)
    details.taxResidency == "EST"
  }

  def "FundPensionOpeningMandateDetails round-trips through JSONB"() {
    given:
    def mandate = persistMandateWithDetails(aFundPensionOpeningMandateDetails)
    entityManager.clear()

    when:
    def loaded = repository.findByIdAndUserId(mandate.id, savedUser.id)
    def details = (FundPensionOpeningMandateDetails) loaded.details

    then:
    details.mandateType == FUND_PENSION_OPENING
    details.pillar == SECOND
    details.duration == new FundPensionOpeningMandateDetails.FundPensionDuration(20, false)
    details.bankAccountDetails == new BankAccountDetails(ESTONIAN, "EE591254471322749514")
  }

  def "WithdrawalCancellationMandateDetails round-trips through JSONB"() {
    given:
    def mandate = persistMandateWithDetails(new WithdrawalCancellationMandateDetails())
    entityManager.clear()

    when:
    def loaded = repository.findByIdAndUserId(mandate.id, savedUser.id)
    def details = (WithdrawalCancellationMandateDetails) loaded.details

    then:
    details.mandateType == WITHDRAWAL_CANCELLATION
  }

  def "EarlyWithdrawalCancellationMandateDetails round-trips through JSONB"() {
    given:
    def mandate = persistMandateWithDetails(new EarlyWithdrawalCancellationMandateDetails())
    entityManager.clear()

    when:
    def loaded = repository.findByIdAndUserId(mandate.id, savedUser.id)
    def details = (EarlyWithdrawalCancellationMandateDetails) loaded.details

    then:
    details.mandateType == EARLY_WITHDRAWAL_CANCELLATION
  }

  def "SelectionMandateDetails round-trips through JSONB"() {
    given:
    def mandate = persistMandateWithDetails(new SelectionMandateDetails("EE3600109435"))
    entityManager.clear()

    when:
    def loaded = repository.findByIdAndUserId(mandate.id, savedUser.id)
    def details = (SelectionMandateDetails) loaded.details

    then:
    details.mandateType == SELECTION
    details.futureContributionFundIsin == "EE3600109435"
  }

  private Mandate persistMandateWithDetails(MandateDetails details) {
    def mandate = Mandate.builder()
      .user(savedUser)
      .pillar(SECOND.toInt())
      .details(details)
      .address(countryFixture().build())
      .metadata([:])
      .build()
    entityManager.persistAndFlush(mandate)
    return mandate
  }

}
