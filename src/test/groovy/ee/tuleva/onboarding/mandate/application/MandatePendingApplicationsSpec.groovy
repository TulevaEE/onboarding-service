package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.fund.ApiFundResponse
import ee.tuleva.onboarding.pillar.Pillar
import spock.lang.Specification

import static ee.tuleva.onboarding.fund.FundFixture.lhv2ndPillarFund
import static ee.tuleva.onboarding.fund.FundFixture.lhv3rdPillarFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarStockFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING

class MandatePendingApplicationsSpec extends Specification {

  ApplicationService applicationService = Mock()
  MandatePendingApplications mandatePendingApplications = new MandatePendingApplications(applicationService)
  Person person = Mock()

  def tulevaFund2nd = new ApiFundResponse(tuleva2ndPillarStockFund(), Locale.ENGLISH)
  def lhvFund2nd = new ApiFundResponse(lhv2ndPillarFund(), Locale.ENGLISH)
  def tulevaFund3rd = new ApiFundResponse(tuleva3rdPillarFund(), Locale.ENGLISH)
  def lhvFund3rd = new ApiFundResponse(lhv3rdPillarFund(), Locale.ENGLISH)

  def "getPendingExchanges returns exchanges only for transfer applications matching the given pillar"() {
    given:
    def secondPillarExchange = new Exchange(tulevaFund2nd, lhvFund2nd, null, 1.0)
    def thirdPillarExchange = new Exchange(lhvFund3rd, tulevaFund3rd, null, 5.0)
    def secondPillarApplication = Application.<TransferApplicationDetails> builder()
        .details(TransferApplicationDetails.builder()
            .sourceFund(tulevaFund2nd)
            .exchange(secondPillarExchange)
            .build())
        .build()
    def thirdPillarApplication = Application.<TransferApplicationDetails> builder()
        .details(TransferApplicationDetails.builder()
            .sourceFund(lhvFund3rd)
            .exchange(thirdPillarExchange)
            .build())
        .build()

    applicationService.getTransferApplications(PENDING, person) >> [secondPillarApplication, thirdPillarApplication]

    when:
    def pendingExchanges = mandatePendingApplications.getPendingExchanges(Pillar.SECOND, person)

    then:
    pendingExchanges.size() == 1
    pendingExchanges.first().sourceIsin == secondPillarExchange.sourceIsin
    pendingExchanges.first().targetIsin == secondPillarExchange.targetIsin
  }

  def "hasPendingWithdrawals delegates to the application service"() {
    given:
    applicationService.hasPendingWithdrawals(person, Pillar.SECOND) >> hasPending

    expect:
    mandatePendingApplications.hasPendingWithdrawals(person, Pillar.SECOND) == hasPending

    where:
    hasPending << [true, false]
  }
}
