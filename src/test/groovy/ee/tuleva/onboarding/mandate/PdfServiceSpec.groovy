package ee.tuleva.onboarding.mandate

import spock.lang.Specification

class PdfServiceSpec extends Specification {

    def service = new PdfService()

    def "print() works"() {
        when:
        def bytes = service.print()

        then:
        bytes != null
        bytes.length > 0
    }

}
