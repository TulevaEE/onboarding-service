package ee.tuleva.onboarding.comparisons.returns

import com.sun.xml.bind.v2.schemagen.xmlschema.Union
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EPIFundValueRetriever
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnProvider
import spock.lang.Specification

import java.time.LocalDate
import java.time.ZoneOffset

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.FUND
import static ee.tuleva.onboarding.comparisons.returns.Returns.Return.Type.INDEX
import static java.util.Arrays.asList
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
            .key(UnionStockIndexRetriever.KEY)
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
        returnProvider1.getKeys() >> [return1.key]
        returnProvider2.getKeys() >> [return2.key]

        when:
        def theReturns = returnsService.get(person, fromDate, [return1.key, return2.key])

        then:
        with(theReturns) {
            from == fromDate
            returns == [return1, return2]
        }
    }

    def "works with null keys"() {
        given:
        def person = samplePerson()
        def fromDate = LocalDate.parse("2019-08-28")
        def startTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        def pillar = 2

        def return1 = Return.builder()
            .key(UnionStockIndexRetriever.KEY)
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
        returnProvider1.getKeys() >> return1.key
        returnProvider2.getKeys() >> return2.key

        when:
        def theReturns = returnsService.get(person, fromDate, null)

        then:
        with(theReturns) {
            from == fromDate
            returns == [return1, return2]
        }
    }

    def "can filter a single return from a return provider that provides many returns"() {
        given:
        def person = samplePerson()
        def fromDate = LocalDate.parse("2019-08-28")
        def startTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        def pillar = 2

        def return1 = Return.builder()
            .key("EE123")
            .type(FUND)
            .value(0.0123)
            .build()

        def return2 = Return.builder()
            .key("EE234")
            .type(FUND)
            .value(0.0234)
            .build()

        def allReturns = Returns.builder()
            .from(fromDate)
            .returns(asList(return1, return2))
            .build()

        returnProvider1.getReturns(person, startTime, pillar) >> allReturns
        returnProvider2.getReturns(person, startTime, pillar) >> []
        returnProvider1.getKeys() >> [return1.key, return2.key]
        returnProvider2.getKeys() >> []

        when:
        def theReturns = returnsService.get(person, fromDate, [return1.key])

        then:
        with(theReturns) {
            from == fromDate
            returns == [return1]
        }
    }
}
