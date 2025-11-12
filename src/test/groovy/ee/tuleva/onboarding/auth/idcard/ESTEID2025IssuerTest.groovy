package ee.tuleva.onboarding.auth.idcard

import spock.lang.Ignore
import spock.lang.Specification

import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

import static javax.security.auth.x500.X500Principal.RFC1779
import static javax.security.auth.x500.X500Principal.RFC2253

@Ignore
class ESTEID2025IssuerTest extends Specification {

    def "Get ESTEID2025 certificate Subject DN in RFC1779 format"() {
        given:
        def certUrl = new URL("https://crt.eidpki.ee/ESTEID2025.crt")
        def inputStream = certUrl.openStream()
        def certificateFactory = CertificateFactory.getInstance("X.509")
        def cert = (X509Certificate) certificateFactory.generateCertificate(inputStream)
        inputStream.close()

        when:
        def rfc1779 = cert.getSubjectX500Principal().getName(RFC1779)
        def rfc2253 = cert.getSubjectX500Principal().getName(RFC2253)

        then:
        println "RFC1779: ${rfc1779}"
        println "RFC2253: ${rfc2253}"
        true
    }
}
