package ee.tuleva.onboarding.mandate.signature.mobileid

import ee.sk.mid.MidClient
import ee.sk.mid.rest.MidConnector
import ee.tuleva.onboarding.mandate.signature.DigiDocFacade
import spock.lang.Specification

class MobileIdSignerSpec extends Specification {

    MidClient client
    MidConnector connector
    DigiDocFacade digiDocFacade
    MobileIdSigner mobileIdSigner

    def setup() {
        client = Mock(MidClient)
        connector = Mock(MidConnector)
        digiDocFacade = Mock(DigiDocFacade)
        mobileIdSigner = new MobileIdSigner(client, connector, digiDocFacade)
    }

    def "can start signing with mobile id"() {
        // TODO
        when:
        mobileIdSigner.startSign(null, null, null)

        then:
        true
    }

    def "can get signed file"() {
        // TODO
        when:
        mobileIdSigner.getSignedFile(null)

        then:
        true
    }
}
