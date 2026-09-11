package ee.tuleva.onboarding.mandate.batch

import ee.tuleva.onboarding.mandate.generic.MandateDto
import spock.lang.Specification

import static ee.tuleva.onboarding.mandate.MandateFixture.aPartialWithdrawalMandateDetails
import static ee.tuleva.onboarding.mandate.MandateFixture.aPaymentRateChangeMandateDetails
import static ee.tuleva.onboarding.mandate.MandateFixture.aThirdPillarPartialWithdrawalMandateDetails

class MandateBatchDtoSpec extends Specification {

  def "isWithdrawalBatch is true only when at least one mandate is a withdrawal type"() {
    given:
    def withdrawalMandate = MandateDto.builder().details(aPartialWithdrawalMandateDetails).build()
    def nonWithdrawalMandate = MandateDto.builder().details(aPaymentRateChangeMandateDetails).build()

    expect:
    MandateBatchDto.builder().mandates([nonWithdrawalMandate, withdrawalMandate]).build().isWithdrawalBatch()
    !MandateBatchDto.builder().mandates([nonWithdrawalMandate]).build().isWithdrawalBatch()
  }

  def "isBatchOnlyThirdPillarPartialWithdrawal is true only for a single 3rd pillar partial withdrawal mandate"() {
    given:
    def thirdPillarWithdrawal = MandateDto.builder().details(aThirdPillarPartialWithdrawalMandateDetails).build()
    def secondPillarWithdrawal = MandateDto.builder().details(aPartialWithdrawalMandateDetails).build()

    expect:
    MandateBatchDto.builder().mandates([thirdPillarWithdrawal]).build().isBatchOnlyThirdPillarPartialWithdrawal()
    !MandateBatchDto.builder().mandates([secondPillarWithdrawal]).build().isBatchOnlyThirdPillarPartialWithdrawal()
    !MandateBatchDto.builder().mandates([thirdPillarWithdrawal, secondPillarWithdrawal]).build().isBatchOnlyThirdPillarPartialWithdrawal()
  }
}
