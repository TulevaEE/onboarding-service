package ee.tuleva.onboarding.mandate.content

import ee.tuleva.onboarding.fund.FundRepository
import spock.lang.Specification

import static ee.tuleva.onboarding.mandate.MandateFixture.aThirdPillarPartialWithdrawalMandateDetails
import static ee.tuleva.onboarding.mandate.MandateFixture.samplePartialWithdrawalMandate

class PartialWithdrawalMandateFileCreatorSpec extends Specification {

  MandateContentService mandateContentService = Mock()
  FundRepository fundRepository = Mock()
  PartialWithdrawalMandateFileCreator creator = new PartialWithdrawalMandateFileCreator(mandateContentService, fundRepository)

  def "getFileName names the file after the pillar-specific withdrawal type"() {
    expect:
    creator.getFileName(samplePartialWithdrawalMandate()) == "yhekordse_valjamakse_avaldus123.html"
    creator.getFileName(samplePartialWithdrawalMandate(aThirdPillarPartialWithdrawalMandateDetails)) == "tagasivotmise_avaldus123.html"
  }
}
