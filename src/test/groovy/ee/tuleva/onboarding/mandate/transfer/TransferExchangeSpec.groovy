package ee.tuleva.onboarding.mandate.transfer

import ee.tuleva.onboarding.fund.Fund
import spock.lang.Specification

class TransferExchangeSpec extends Specification {

    def "get pillar works"() {
        when:
        def transferExchange = TransferExchange.builder()
            .sourceFund(Fund.builder().pillar(2).build())
            .targetFund(Fund.builder().pillar(2).build())
            .build()

        then:
        transferExchange.pillar == 2
    }

    def "get pillar does validation"() {
        given:
        def transferExchange = TransferExchange.builder()
            .sourceFund(Fund.builder().pillar(2).build())
            .targetFund(Fund.builder().pillar(3).build())
            .build()

        when:
        transferExchange.pillar

        then:
        thrown IllegalStateException
    }

}
