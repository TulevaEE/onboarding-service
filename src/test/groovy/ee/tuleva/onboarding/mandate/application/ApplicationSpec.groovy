package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.applicationtype.ApplicationType
import ee.tuleva.onboarding.fund.ApiFundResponse
import ee.tuleva.onboarding.fund.Fund
import spock.lang.Specification

import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.*
import static ee.tuleva.onboarding.applicationtype.ApplicationType.EARLY_WITHDRAWAL
import static ee.tuleva.onboarding.applicationtype.ApplicationType.TRANSFER
import static ee.tuleva.onboarding.applicationtype.ApplicationType.WITHDRAWAL

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

  def "isComplete"() {
    expect:
    Application.builder().status(COMPLETE).build().isComplete()
    !Application.builder().status(PENDING).build().isComplete()
    !Application.builder().status(FAILED).build().isComplete()
    !Application.builder().build().isComplete()
  }

  def "hasStatus"() {
    expect:
    Application.builder().status(PENDING).build().hasStatus(PENDING)
    !Application.builder().status(PENDING).build().hasStatus(COMPLETE)
    !Application.builder().build().hasStatus(PENDING)
  }

  def "getPillar"() {
    def secondPillarFund = new ApiFundResponse(Fund.builder().pillar(2).build(), Locale.ENGLISH)
    def secondPillarExchange = new Exchange(secondPillarFund, secondPillarFund, null, BigDecimal.ONE)

    expect:
    Application.builder()
        .details(
            TransferApplicationDetails.builder().sourceFund(secondPillarFund).exchange(secondPillarExchange).build()
        )
        .build()
        .getPillar() == 2
  }

  def "getPillar throws when the transfer exchanges span different pillars than the source fund"() {
    def secondPillarFund = new ApiFundResponse(Fund.builder().pillar(2).build(), Locale.ENGLISH)
    def thirdPillarFund = new ApiFundResponse(Fund.builder().pillar(3).build(), Locale.ENGLISH)
    def thirdPillarExchange = new Exchange(thirdPillarFund, thirdPillarFund, null, BigDecimal.TEN)

    when:
    Application.builder()
        .details(
            TransferApplicationDetails.builder().sourceFund(secondPillarFund).exchange(thirdPillarExchange).build()
        )
        .build()
        .getPillar()

    then:
    thrown(IllegalStateException)
  }
}
