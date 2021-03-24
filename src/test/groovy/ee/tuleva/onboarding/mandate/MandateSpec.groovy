package ee.tuleva.onboarding.mandate


import spock.lang.Specification

import static ee.tuleva.onboarding.mandate.application.ApplicationType.EARLY_WITHDRAWAL
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL

class MandateSpec extends Specification {

  def "can get applicationTypeToCancel"() {
    when:
    Mandate mandate = Mandate.builder().metadata(metadata).build()

    then:
    mandate.isWithdrawalCancellation() == isCancellation
    mandate.getApplicationTypeToCancel() == applicationTypeToCancel

    where:
    metadata                                        | isCancellation | applicationTypeToCancel
    new HashMap<String, Object>()                   | false          | null
    ["applicationTypeToCancel": "WITHDRAWAL"]       | true           | WITHDRAWAL
    ["applicationTypeToCancel": "EARLY_WITHDRAWAL"] | true           | EARLY_WITHDRAWAL
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
}
