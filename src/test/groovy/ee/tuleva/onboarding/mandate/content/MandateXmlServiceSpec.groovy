package ee.tuleva.onboarding.mandate.content

import ee.tuleva.onboarding.mandate.MandateService
import spock.lang.Specification

class MandateXmlServiceSpec extends Specification {

    MandateService mandateService = Mock(MandateService)
    MandateXmlService mandateXmlService = new MandateXmlService(mandateService)

    def "getRequestContents"() {

    }


}
