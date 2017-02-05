package ee.tuleva.onboarding.comparisons

import ee.tuleva.onboarding.comparisons.exceptions.IsinNotFoundException
import ee.tuleva.onboarding.comparisons.exceptions.SourceHTMLChangedException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import spock.lang.Specification

class PensionikeskusCodeToIsinSpec extends Specification{

    def pk = new PensionikeskusCodeToIsin()

    def setup(){
        pk.map.put(1,"testisin---1")

    }

    def "finding existing isin works" () {

        expect:
        pk.getIsin(1) == "testisin---1"

    }

    def "distinguishing invalid bank code" (){

        when:
        pk.getIsin(2)

        then:
        thrown(SourceHTMLChangedException)

    }

    def "finds new isin from website" (){

        given:
        File fundData = new File("src/test/resources/funddata.txt")
        Document html = Jsoup.parse(fundData,"UTF-8")

        expect:
        pk.findNewIsinFromHTML(html) == "EE3600019790"

    }

    def "detects changed isin location in html" (){

        given:
        File fundData = new File("src/test/resources/funddataIsinMalformed.txt")
        Document html = Jsoup.parse(fundData,"UTF-8")

        when:
        pk.findNewIsinFromHTML(html)

        then:
        thrown(IsinNotFoundException)

    }

    def "detects changed html structure" (){

        given:
        File fundData = new File("src/test/resources/funddataStructureChanged.txt")
        Document html = Jsoup.parse(fundData,"UTF-8")

        when:
        pk.findNewIsinFromHTML(html)

        then:
        thrown(SourceHTMLChangedException)

    }


}
