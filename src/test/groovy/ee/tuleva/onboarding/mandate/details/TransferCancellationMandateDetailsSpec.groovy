package ee.tuleva.onboarding.mandate.details

import spock.lang.Specification

import static ee.tuleva.onboarding.applicationtype.ApplicationType.CANCELLATION
import static ee.tuleva.onboarding.pillar.Pillar.SECOND

class TransferCancellationMandateDetailsSpec extends Specification {

  def "getApplicationType is CANCELLATION"() {
    expect:
    new TransferCancellationMandateDetails("SOURCE_ISIN", SECOND).getApplicationType() == CANCELLATION
  }

  def "toFundTransferExchanges returns a single cancellation exchange for the source isin"() {
    given:
    def details = new TransferCancellationMandateDetails("SOURCE_ISIN", SECOND)

    when:
    def exchanges = details.toFundTransferExchanges()

    then:
    exchanges.size() == 1
    exchanges.first().sourceFundIsin == "SOURCE_ISIN"
    exchanges.first().targetFundIsin == null
    exchanges.first().amount == null
  }
}
