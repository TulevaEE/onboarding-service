package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.smartid.OcspResponderFixture.RESPONDER_URL;
import static ee.tuleva.onboarding.auth.smartid.OcspResponderFixture.authenticationCertificateIssuedBy;
import static ee.tuleva.onboarding.auth.smartid.OcspResponderFixture.caCertificate;
import static ee.tuleva.onboarding.auth.smartid.OcspResponderFixture.issuedBy;
import static ee.tuleva.onboarding.auth.smartid.OcspResponderFixture.keyPair;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.tuleva.onboarding.auth.ocsp.OCSPUtils;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

class SmartIdCertificateRevocationCheckTest {

  private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
  private static final MediaType OCSP_RESPONSE =
      MediaType.parseMediaType("application/ocsp-response");

  private final OcspResponderFixture fixture = new OcspResponderFixture();
  private final RestClient.Builder restClientBuilder = RestClient.builder();
  private final MockRestServiceServer responder =
      MockRestServiceServer.bindTo(restClientBuilder).build();

  private SmartIdCertificateRevocationCheck check() {
    return new SmartIdCertificateRevocationCheck(
        List.of(fixture.ca),
        restClientBuilder.build(),
        new OCSPUtils(),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private ResponseCreator answering(OcspResponderFixture.Answer answer) {
    return request ->
        withSuccess(
                fixture.respond(((MockClientHttpRequest) request).getBodyAsBytes(), answer),
                OCSP_RESPONSE)
            .createResponse(request);
  }

  private void responderAnswers(OcspResponderFixture.Answer answer) {
    responder
        .expect(requestTo(RESPONDER_URL))
        .andExpect(method(POST))
        .andExpect(header("Content-Type", "application/ocsp-request"))
        .andRespond(answering(answer));
  }

  @Test
  void letsALoginThroughWhenTheResponderInTheCertificateSaysGood() {
    responderAnswers(fixture.answering(CertificateStatus.GOOD, NOW));

    assertThatCode(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .doesNotThrowAnyException();
    responder.verify();
  }

  @Test
  void refusesARevokedCertificate() {
    responderAnswers(
        fixture.answering(new RevokedStatus(Date.from(NOW.minus(Duration.ofDays(1))), 1), NOW));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateRevokedException.class);
  }

  @Test
  void refusesACertificateTheResponderDoesNotKnow() {
    responderAnswers(fixture.answering(new UnknownStatus(), NOW));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateRevokedException.class);
  }

  @Test
  void cannotTellWhenTheResponderFails() {
    responder.expect(requestTo(RESPONDER_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
  }

  @Test
  void cannotTellWhenTheResponderIsUnreachable() {
    responder
        .expect(requestTo(RESPONDER_URL))
        .andRespond(
            request -> {
              throw new SocketTimeoutException("read timed out");
            });

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
  }

  @Test
  void cannotTellFromAResponseThatIsNotOcsp() {
    responder
        .expect(requestTo(RESPONDER_URL))
        .andRespond(withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
  }

  @Test
  void cannotTellWhenTheResponderAsksToTryLater() {
    responder
        .expect(requestTo(RESPONDER_URL))
        .andRespond(
            withSuccess(
                OcspResponderFixture.unsuccessful(OCSPRespBuilder.TRY_LATER), OCSP_RESPONSE));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
  }

  @Test
  void distrustsAGoodAnswerThatDoesNotEchoOurNonce() {
    var replayed =
        new OcspResponderFixture.Answer(
            CertificateStatus.GOOD,
            NOW,
            false,
            fixture.responderKeys,
            fixture.responder,
            fixture.authenticationCertificate.getSerialNumber());
    responderAnswers(replayed);

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
  }

  @Test
  void distrustsAGoodAnswerSignedWithAKeyOtherThanTheResponderCertificates() {
    var forged =
        new OcspResponderFixture.Answer(
            CertificateStatus.GOOD,
            NOW,
            true,
            keyPair(),
            fixture.responder,
            fixture.authenticationCertificate.getSerialNumber());
    responderAnswers(forged);

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
  }

  @Test
  void distrustsAGoodAnswerFromAResponderAnotherCaCertified() {
    KeyPair otherCaKeys = keyPair();
    X509Certificate otherCa = caCertificate("CN=Some other CA", otherCaKeys);
    KeyPair impostorKeys = keyPair();
    X509Certificate impostor =
        issuedBy(otherCa, otherCaKeys, "CN=Impostor OCSP responder", impostorKeys, true);
    responderAnswers(
        new OcspResponderFixture.Answer(
            CertificateStatus.GOOD,
            NOW,
            true,
            impostorKeys,
            impostor,
            fixture.authenticationCertificate.getSerialNumber()));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
  }

  @Test
  void distrustsAGoodAnswerFromACertificateTheIssuerDidNotAuthoriseForOcsp() {
    KeyPair keys = keyPair();
    X509Certificate notAResponder =
        issuedBy(fixture.ca, fixture.caKeys, "CN=Not an OCSP responder", keys, false);
    responderAnswers(
        new OcspResponderFixture.Answer(
            CertificateStatus.GOOD,
            NOW,
            true,
            keys,
            notAResponder,
            fixture.authenticationCertificate.getSerialNumber()));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
  }

  @Test
  void distrustsAGoodAnswerAboutAnotherCertificate() {
    responderAnswers(
        new OcspResponderFixture.Answer(
            CertificateStatus.GOOD,
            NOW,
            true,
            fixture.responderKeys,
            fixture.responder,
            BigInteger.valueOf(424242)));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
  }

  @Test
  void distrustsAGoodAnswerProducedLongBeforeWeAsked() {
    responderAnswers(fixture.answering(CertificateStatus.GOOD, NOW.minus(Duration.ofMinutes(16))));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
  }

  @Test
  void distrustsAGoodAnswerDatedInTheFuture() {
    responderAnswers(fixture.answering(CertificateStatus.GOOD, NOW.plus(Duration.ofMinutes(16))));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
  }

  @Test
  void cannotTellForACertificateNoBundledCaIssued() {
    KeyPair strangerCaKeys = keyPair();
    X509Certificate strangerCa = caCertificate("CN=Stranger CA", strangerCaKeys);
    X509Certificate certificate =
        authenticationCertificateIssuedBy(strangerCa, strangerCaKeys, RESPONDER_URL);

    assertThatThrownBy(() -> check().requireNotRevoked(certificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
    responder.verify();
  }

  @Test
  void acceptsAGoodAnswerWithinFifteenMinutesOfOurClock() {
    responderAnswers(fixture.answering(CertificateStatus.GOOD, NOW.minus(Duration.ofMinutes(14))));

    assertThatCode(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .doesNotThrowAnyException();
  }
}
