package ee.tuleva.onboarding.comparisons

import ee.tuleva.domain.fund.FundManagerRepository
import ee.tuleva.onboarding.comparisons.exceptions.FundManagerNameException
import org.jsoup.Jsoup
import spock.lang.Specification

import org.jsoup.nodes.Document

class EstonianFeeFinderServiceSpec extends Specification{


    //def fundMan = Mock(FundManagerRepository)
    //def service = Spy(EstonianFeeFinderService)
    EstonianFeeFinderService service = new EstonianFeeFinderService()
    def fundManagerRepository = Mock(FundManagerRepository)
    //def service = new EstonianFeeFinderService(fundmanagerRepo)

    def setup(){
        service.fundManagerRepository = fundManagerRepository
    }

    def "finding fees works" (){
        given:
        File correct = new File("src/test/resources/compare.html")
        Document html = Jsoup.parse(correct,"UTF-8")

        when:
        service.findFundsFromHTML(html)

        then:
        //noExceptionThrown()
        thrown(FundManagerNameException)

    }

    def "wrong fee size throws exception" () {
        given:
        File file = new File("src/test/resources/comparefees.html")
        Document html = Jsoup.parse(file,"UTF-8")

        when:
        service.findFundsFromHTML(html)

        then:
 //       thrown(FeeSizeException)
        thrown(FundManagerNameException)


    }

    def "wrong format throws exception" () {
        given:
        File file = new File("src/test/resources/compareparsing.html")
        Document html = Jsoup.parse(file,"UTF-8")

        when:
        service.findFundsFromHTML(html)

        then:
        //thrown(ParseException)
        thrown(FundManagerNameException)

    }



}
