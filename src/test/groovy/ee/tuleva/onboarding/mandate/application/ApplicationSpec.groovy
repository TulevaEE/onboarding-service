package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.ApiFundResponse
import spock.lang.Specification

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.*
import static ee.tuleva.onboarding.mandate.application.ApplicationType.*

class ApplicationSpec extends Specification {

  def "isTransfer"() {
    expect:
    Application.builder().type(TRANSFER).build().isTransfer()
    TransferApplication.builder().type(TRANSFER).build().isTransfer()
    !Application.builder().type(WITHDRAWAL).build().isTransfer()
  }

  def "isPending"() {
    expect:
    Application.builder().status(PENDING).build().isPending()
    TransferApplication.builder().status(PENDING).build().isPending()
    !Application.builder().status(COMPLETE).build().isPending()
    !Application.builder().status(FAILED).build().isPending()
  }

  def "isWithdrawal"() {
    expect:
    Application.builder().type(WITHDRAWAL).build().isWithdrawal()
    Application.builder().type(EARLY_WITHDRAWAL).build().isWithdrawal()
    !Application.builder().build().isWithdrawal()
    !Application.builder().type(TRANSFER).build().isWithdrawal()
    !TransferApplication.builder().type(TRANSFER).build().isWithdrawal()
  }

  def "getPillar"() {
    def secondPillarFund = new ApiFundResponse(Fund.builder().pillar(2).build(), "en")
    def secondPillarExchange = new Exchange(secondPillarFund, secondPillarFund, null, null)

    expect:
    Application.builder()
        .details(
            TransferApplicationDetails.builder().sourceFund(secondPillarFund).exchange(secondPillarExchange).build()
        )
        .build()
        .getPillar() == 2

    TransferApplication.builder()
        .details(
            TransferApplicationDetails.builder().sourceFund(secondPillarFund).exchange(secondPillarExchange).build()
        )
        .build()
        .getPillar() == 2

  }
}
