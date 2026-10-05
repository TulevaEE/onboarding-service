package ee.tuleva.onboarding.auth.smartid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers.id_pkix_ocsp_nonce;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class OcspResponseVerifierLiveResponderTest {

  @Test
  void acceptsTheAnswerSksLiveEidQ2024EResponderGaveOurRequest() throws Exception {
    CertificateStatus status =
        verifiedRecordedAnswer(
            "live-eid-q-2024e",
            "smart-id/live/SK_ID_Solutions_EID-Q_2024E.pem",
            Instant.parse("2026-10-01T20:58:50Z"));

    assertThat(status).isInstanceOf(UnknownStatus.class);
  }

  @Test
  void acceptsTheAnswerSksLiveEidSk2016ResponderGaveOurRequest() throws Exception {
    CertificateStatus status =
        verifiedRecordedAnswer(
            "live-eid-sk-2016",
            "smart-id/live/EID-SK_2016.pem",
            Instant.parse("2026-10-01T20:58:51Z"));

    assertThat(status).isInstanceOf(RevokedStatus.class);
  }

  private static CertificateStatus verifiedRecordedAnswer(
      String recording, String issuer, Instant answeredAt) throws Exception {
    OCSPReq request = new OCSPReq(bytes("smart-id/ocsp/" + recording + "-request.der"));
    return new OcspResponseVerifier(Clock.fixed(answeredAt, ZoneOffset.UTC))
        .verifiedStatus(
            new OCSPResp(bytes("smart-id/ocsp/" + recording + "-response.der")),
            request.getRequestList()[0].getCertID(),
            request.getExtension(id_pkix_ocsp_nonce),
            certificate(issuer));
  }

  private static byte[] bytes(String path) throws IOException {
    try (InputStream input = new ClassPathResource(path).getInputStream()) {
      return input.readAllBytes();
    }
  }

  private static X509Certificate certificate(String path) throws IOException, CertificateException {
    try (InputStream input = new ClassPathResource(path).getInputStream()) {
      return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
    }
  }
}
