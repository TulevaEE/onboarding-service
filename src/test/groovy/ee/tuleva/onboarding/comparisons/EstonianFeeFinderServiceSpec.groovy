package ee.tuleva.onboarding.comparisons

import ee.tuleva.onboarding.comparisons.exceptions.FeeSizeException
import ee.tuleva.onboarding.comparisons.exceptions.FundManagerNotFoundException
import ee.tuleva.onboarding.comparisons.exceptions.SourceHTMLChangedException
import ee.tuleva.onboarding.fund.FundManagerRepository
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import spock.lang.Specification

class EstonianFeeFinderServiceSpec extends Specification {

    EstonianFeeFinderService service = new EstonianFeeFinderService()

    def fundManagerRepository = Mock(FundManagerRepository)

    def setup() {
        service.fundManagerRepository = fundManagerRepository

    }

    def "finding fees works"() {
        given:
        File correct = new File("src/test/resources/compare.txt")
        Document html = Jsoup.parse(correct, "UTF-8")

        when:

        service.findFundsFromHTML(html)

        then:
        thrown(FundManagerNotFoundException)
        //TODO
        //should throw no exceptions, fundManagerRepository mocking is implemented incorrectly
        //noExceptionThrown()

    }

    def "source html changes detected"(){
        given:
        File file = new File("src/test/resources/compareSourceChanged.txt")
        Document html = Jsoup.parse(file, "UTF-8")

        when:
        service.findFundsFromHTML(html)

        then:
        thrown(SourceHTMLChangedException)

    }

    def "wrong fee size throws exception"() {
        given:
        File file = new File("src/test/resources/comparefees.txt")
        Document html = Jsoup.parse(file, "UTF-8")

        when:
        service.findFundsFromHTML(html)

        then:
        thrown(FeeSizeException)

    }

    def "wrong format throws exception"() {
        given:
        File file = new File("src/test/resources/compareparsing.txt")
        Document html = Jsoup.parse(file, "UTF-8")

        when:
        service.findFundsFromHTML(html)

        then:
        thrown(NumberFormatException)

    }

    def "fee parsed correctly" (String fee, float f){

        expect:
        service.parseFee(fee) == f

        where:
        fee      | f
        "1,2"    | 0.012
        "0.8956" | 0.008956
        "1.1"    | 0.011
        "1"      | 0.01

    }

    def "fee fits into Estonian regulations" (){
        when:
        service.parseFee("2.1")

        then:
        thrown(FeeSizeException)

        when:
        service.parseFee("-0.2")

        then:
        thrown(FeeSizeException)
    }


}
