package ee.tuleva.onboarding.mandate.email

import ee.tuleva.onboarding.mandate.PillarSuggestion
import spock.lang.Specification

import static ee.tuleva.onboarding.mandate.MandateFixture.*
import static ee.tuleva.onboarding.mandate.batch.MandateBatchFixture.aSavedMandateBatch
import static ee.tuleva.onboarding.notification.email.EmailType.*

class MandateEmailTypeSpec extends Specification {

  def "emailTypeFor(Mandate) maps the mandate kind to the matching email type"() {
    expect:
    MandateEmailType.emailTypeFor(sampleMandateWithPaymentRate()) == SECOND_PILLAR_PAYMENT_RATE
    MandateEmailType.emailTypeFor(sampleWithdrawalCancellationMandate()) == SECOND_PILLAR_WITHDRAWAL_CANCELLATION
    MandateEmailType.emailTypeFor(sampleEarlyWithdrawalCancellationMandate()) == SECOND_PILLAR_WITHDRAWAL_CANCELLATION
    MandateEmailType.emailTypeFor(sampleTransferCancellationMandate()) == SECOND_PILLAR_TRANSFER_CANCELLATION
    MandateEmailType.emailTypeFor(thirdPillarMandate()) == THIRD_PILLAR_PAYMENT_REMINDER_MANDATE
    MandateEmailType.emailTypeFor(sampleMandate()) == SECOND_PILLAR_MANDATE
  }

  def "emailTypeFor(MandateBatch) requires every mandate in the batch to be a withdrawal type"() {
    given:
    def fundPensionMandate = sampleFundPensionOpeningMandate(aFundPensionOpeningMandateDetails)
    def withdrawalMandate = samplePartialWithdrawalMandate(aPartialWithdrawalMandateDetails)
    def nonWithdrawalMandate = sampleMandateWithPaymentRate()

    expect:
    MandateEmailType.emailTypeFor(aSavedMandateBatch([fundPensionMandate, withdrawalMandate])) == WITHDRAWAL_BATCH

    when:
    MandateEmailType.emailTypeFor(aSavedMandateBatch([fundPensionMandate, nonWithdrawalMandate]))

    then:
    thrown(IllegalArgumentException)
  }

  def "emailTypeFor(Mandate, PillarSuggestion) always resolves to the second pillar suggestion nudge"() {
    given:
    def pillarSuggestion = Mock(PillarSuggestion)
    pillarSuggestion.isSuggestSecondPillar() >> suggestSecondPillar

    expect:
    MandateEmailType.emailTypeFor(thirdPillarMandate(), pillarSuggestion) == THIRD_PILLAR_SUGGEST_SECOND

    where:
    suggestSecondPillar << [true, false]
  }
}
