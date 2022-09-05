package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.fund.ApiFundResponse
import ee.tuleva.onboarding.fund.Fund
import spock.lang.Specification

import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.*
import static ee.tuleva.onboarding.mandate.application.ApplicationType.EARLY_WITHDRAWAL
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL

class ApplicationSpec extends Specification {

  def "isTransfer"() {
    expect:
    Application.<TransferApplicationDetails> builder()
        .details(TransferApplicationDetails.builder().build()).build().isTransfer()
    Application.<TransferApplicationDetails> builder()
        .details(TransferApplicationDetails.builder().type(TRANSFER).build()).build().isTransfer()
    !Application.builder().build().isTransfer()
    !Application.<TransferApplicationDetails> builder().build().isTransfer()
    !Application.<WithdrawalApplicationDetails> builder().build().isTransfer()
    !Application.<WithdrawalApplicationDetails> builder()
        .details(WithdrawalApplicationDetails.builder().type(WITHDRAWAL).build()).build().isTransfer()
    !Application.<WithdrawalApplicationDetails> builder()
        .details(WithdrawalApplicationDetails.builder().type(EARLY_WITHDRAWAL).build()).build().isTransfer()
  }

  def "isWithdrawal"() {
    expect:
    Application.<WithdrawalApplicationDetails> builder()
        .details(WithdrawalApplicationDetails.builder().type(WITHDRAWAL).build())
        .build().isWithdrawal()
    Application.<WithdrawalApplicationDetails> builder()
        .details(WithdrawalApplicationDetails.builder().type(EARLY_WITHDRAWAL).build())
        .build().isWithdrawal()
    !Application.builder().build().isWithdrawal()
    !Application.<TransferApplicationDetails> builder().build().isWithdrawal()
    !Application.<TransferApplicationDetails> builder()
        .details(TransferApplicationDetails.builder().build()).build().isWithdrawal()
    !Application.<TransferApplicationDetails> builder()
        .details(TransferApplicationDetails.builder().type(TRANSFER).build()).build().isWithdrawal()
  }

  def "isPending"() {
    expect:
    Application.builder().status(PENDING).build().isPending()
    !Application.builder().status(COMPLETE).build().isPending()
    !Application.builder().status(FAILED).build().isPending()
    !Application.builder().build().isPending()
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
  }
}
