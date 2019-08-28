package ee.tuleva.onboarding.comparisons.returns

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EPIFundValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.WorldIndexValueRetriever
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnProvider
import spock.lang.Specification

import java.time.LocalDate
import java.time.ZoneOffset

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.INDEX
import static java.util.Collections.singletonList

class ReturnsServiceSpec extends Specification {

    def returnProvider1 = Mock(ReturnProvider)
    def returnProvider2 = Mock(ReturnProvider)
    def returnsService = new ReturnsService([returnProvider1, returnProvider2])

    def "can get returns from multiple providers"() {
        given:
        def person = samplePerson()
        def fromDate = LocalDate.parse("2019-08-28")
        def startTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        def pillar = 2

        def return1 = Return.builder()
            .key(WorldIndexValueRetriever.KEY)
            .type(INDEX)
            .value(0.0123)
            .build()

        def returns1 = Returns.builder()
            .from(fromDate)
            .returns(singletonList(return1))
            .build()

        def return2 = Return.builder()
            .key(EPIFundValueRetriever.KEY)
            .type(INDEX)
            .value(0.0234)
            .build()

        def returns2 = Returns.builder()
            .from(fromDate)
            .returns(singletonList(return2))
            .build()

        returnProvider1.getReturns(person, startTime, pillar) >> returns1
        returnProvider2.getReturns(person, startTime, pillar) >> returns2

        when:
        def returns = returnsService.getReturns(person, fromDate)

        then:
        returns == Returns.builder()
            .from(fromDate)
            .returns([return1, return2])
            .build()
    }
}
