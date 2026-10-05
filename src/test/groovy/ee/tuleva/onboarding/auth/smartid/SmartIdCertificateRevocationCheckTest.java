package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.smartid.OcspResponderFixture.RESPONDER_URL;
import static ee.tuleva.onboarding.auth.smartid.OcspResponderFixture.authenticationCertificateIssuedBy;
import static ee.tuleva.onboarding.auth.smartid.OcspResponderFixture.caCertificate;
import static ee.tuleva.onboarding.auth.smartid.OcspResponderFixture.issuedBy;
import static ee.tuleva.onboarding.auth.smartid.OcspResponderFixture.keyPair;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ee.sk.smartid.DefaultTrustedCAStoreBuilder;
import ee.tuleva.onboarding.auth.ocsp.OCSPUtils;
import ee.tuleva.onboarding.config.SmartIdClientConfiguration;
import java.io.EOFException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.security.KeyPair;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpStatus;
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
    return checkRetrying(theConfiguredRetryDecisionsAskingAgainAtOnce().build());
  }

  private SmartIdCertificateRevocationCheck checkRetryingWithin(Duration retryWindow) {
    return checkRetrying(
        theConfiguredRetryDecisionsAskingAgainAtOnce().timeout(retryWindow).build());
  }

  private static RetryPolicy.Builder theConfiguredRetryDecisionsAskingAgainAtOnce() {
    RetryPolicy configured =
        new SmartIdClientConfiguration().smartIdOcspRetryTemplate().getRetryPolicy();
    return RetryPolicy.builder()
        .predicate(configured::shouldRetry)
        .maxRetries(2)
        .delay(Duration.ZERO);
  }

  private SmartIdCertificateRevocationCheck checkRetrying(RetryPolicy retryPolicy) {
    return new SmartIdCertificateRevocationCheck(
        new DefaultTrustedCAStoreBuilder()
            .withTrustAnchors(Set.of(new TrustAnchor(fixture.root, null)))
            .withIntermediateCACertificate(List.of(fixture.ca))
            .withOcspEnabled(false)
            .build(),
        restClientBuilder.build(),
        new OCSPUtils(),
        Clock.fixed(NOW, ZoneOffset.UTC),
        new RetryTemplate(retryPolicy));
  }

  @SneakyThrows
  private static void waitAtLeast(Duration duration) {
    Thread.sleep(duration);
  }

  private ResponseCreator answering(OcspResponderFixture.Answer answer) {
    return request ->
        withSuccess(
                fixture.respond(((MockClientHttpRequest) request).getBodyAsBytes(), answer),
                OCSP_RESPONSE)
            .createResponse(request);
  }

  private static ResponseCreator answeringUnsuccessfully(int responseStatus) {
    return withSuccess(OcspResponderFixture.unsuccessful(responseStatus), OCSP_RESPONSE);
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
  void refusesARevokedCertificateWithoutAskingAgain() {
    responderAnswers(
        fixture.answering(new RevokedStatus(Date.from(NOW.minus(Duration.ofDays(1))), 1), NOW));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateRevokedException.class);
    responder.verify();
  }

  @Test
  void refusesACertificateTheResponderDoesNotKnow() {
    responderAnswers(fixture.answering(new UnknownStatus(), NOW));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateRevokedException.class);
  }

  @Test
  void cannotTellWhenTheResponderKeepsFailingForThreeAttempts() {
    responder.expect(times(3), requestTo(RESPONDER_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
    responder.verify();
  }

  @Test
  void cannotTellWhenTheResponderKeepsRefusingTheConnectionForThreeAttempts() {
    responder
        .expect(times(3), requestTo(RESPONDER_URL))
        .andRespond(
            request -> {
              throw new ConnectException("connection refused");
            });

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
    responder.verify();
  }

  @Test
  void cannotTellWhenTheResponderKeepsAnsweringEmptyForThreeAttempts() {
    responder.expect(times(3), requestTo(RESPONDER_URL)).andRespond(withSuccess());

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
    responder.verify();
  }

  @Test
  void cannotTellWithoutAskingAgainWhenTheResponderFailsTooSlowlyToRetryWithinTheWindow() {
    responder
        .expect(once(), requestTo(RESPONDER_URL))
        .andRespond(
            request -> {
              waitAtLeast(Duration.ofMillis(100));
              throw new SocketTimeoutException("read timed out");
            });

    assertThatThrownBy(
            () ->
                checkRetryingWithin(Duration.ofMillis(50))
                    .requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
    responder.verify();
  }

  @Test
  void letsALoginThroughWhenTheResponderAnswersGoodAfterTheConnectionDropped() {
    responder
        .expect(requestTo(RESPONDER_URL))
        .andRespond(
            request -> {
              throw new EOFException("EOF reached while reading");
            });
    responderAnswers(fixture.answering(CertificateStatus.GOOD, NOW));

    assertThatCode(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .doesNotThrowAnyException();
    responder.verify();
  }

  @Test
  void letsALoginThroughWhenTheResponderAnswersGoodAfterAServerError() {
    responder.expect(requestTo(RESPONDER_URL)).andRespond(withServerError());
    responderAnswers(fixture.answering(CertificateStatus.GOOD, NOW));

    assertThatCode(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .doesNotThrowAnyException();
    responder.verify();
  }

  @Test
  void letsALoginThroughWhenTheResponderAnswersGoodAfterAnEmptyResponse() {
    responder.expect(requestTo(RESPONDER_URL)).andRespond(withSuccess());
    responderAnswers(fixture.answering(CertificateStatus.GOOD, NOW));

    assertThatCode(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .doesNotThrowAnyException();
    responder.verify();
  }

  @ParameterizedTest
  @EnumSource(names = {"REQUEST_TIMEOUT", "TOO_MANY_REQUESTS"})
  void letsALoginThroughWhenTheResponderAnswersGoodAfterRefusingOnlyForNow(HttpStatus status) {
    responder.expect(requestTo(RESPONDER_URL)).andRespond(withStatus(status));
    responderAnswers(fixture.answering(CertificateStatus.GOOD, NOW));

    assertThatCode(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .doesNotThrowAnyException();
    responder.verify();
  }

  @Test
  void cannotTellWithoutAskingAgainWhenTheResponderRejectsTheRequest() {
    responder.expect(once(), requestTo(RESPONDER_URL)).andRespond(withBadRequest());

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
    responder.verify();
  }

  @Test
  void cannotTellWithoutAskingAgainFromAResponseThatIsNotOcsp() {
    responder
        .expect(once(), requestTo(RESPONDER_URL))
        .andRespond(withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
    responder.verify();
  }

  @Test
  void letsALoginThroughWhenTheResponderAnswersGoodAfterAskingToTryLater() {
    responder
        .expect(requestTo(RESPONDER_URL))
        .andRespond(answeringUnsuccessfully(OCSPRespBuilder.TRY_LATER));
    responderAnswers(fixture.answering(CertificateStatus.GOOD, NOW));

    assertThatCode(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .doesNotThrowAnyException();
    responder.verify();
  }

  @Test
  void cannotTellWhenTheResponderKeepsAskingToTryLaterForThreeAttempts() {
    responder
        .expect(times(3), requestTo(RESPONDER_URL))
        .andRespond(answeringUnsuccessfully(OCSPRespBuilder.TRY_LATER));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
    responder.verify();
  }

  @Test
  void letsALoginThroughWhenTheResponderAnswersGoodAfterAnsweringWithItsOwnInternalError() {
    responder
        .expect(requestTo(RESPONDER_URL))
        .andRespond(answeringUnsuccessfully(OCSPRespBuilder.INTERNAL_ERROR));
    responderAnswers(fixture.answering(CertificateStatus.GOOD, NOW));

    assertThatCode(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .doesNotThrowAnyException();
    responder.verify();
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        OCSPRespBuilder.MALFORMED_REQUEST,
        OCSPRespBuilder.UNAUTHORIZED,
        OCSPRespBuilder.SIG_REQUIRED
      })
  void cannotTellWithoutAskingAgainWhenTheResponderRefusesOurRequest(int responseStatus) {
    responder
        .expect(once(), requestTo(RESPONDER_URL))
        .andRespond(answeringUnsuccessfully(responseStatus));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
    responder.verify();
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

  @Test
  void acceptsAGoodAnswerTheIssuingCaSignedItselfWithoutIncludingAnyCertificate() {
    responderAnswers(fixture.signedByTheIssuingCa(CertificateStatus.GOOD, NOW));

    assertThatCode(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsAGoodAnswerTheIssuingCaSignedAndIncludedItsOwnCertificateIn() {
    responderAnswers(
        fixture.signedByTheIssuingCa(CertificateStatus.GOOD, NOW).including(fixture.ca));

    assertThatCode(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsAGoodAnswerFromADelegatedResponderIdentifiedByName() {
    responderAnswers(fixture.answering(CertificateStatus.GOOD, NOW).identifiedByName());

    assertThatCode(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsAGoodAnswerFromADelegatedResponderListedAfterItsCaCertificate() {
    responderAnswers(
        fixture.answering(CertificateStatus.GOOD, NOW).including(fixture.ca, fixture.responder));

    assertThatCode(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsAGoodAnswerFromADelegatedResponderNamedAmongSeveralCertificates() {
    responderAnswers(
        fixture
            .answering(CertificateStatus.GOOD, NOW)
            .identifiedByName()
            .including(fixture.root, fixture.responder, fixture.ca));

    assertThatCode(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .doesNotThrowAnyException();
  }

  @Test
  void distrustsAGoodAnswerWhoseResponderCertificateIsNotIncluded() {
    responderAnswers(
        fixture.answering(CertificateStatus.GOOD, NOW).identifiedByName().including(fixture.ca));

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
  }

  @Test
  void distrustsAGoodAnswerSignedByTheResponderButNamingTheIssuingCaAsItsResponder() {
    var answer =
        new OcspResponderFixture.Answer(
                CertificateStatus.GOOD,
                NOW,
                true,
                fixture.responderKeys,
                fixture.ca,
                fixture.authenticationCertificate.getSerialNumber())
            .identifiedByName()
            .including(fixture.responder);
    responderAnswers(answer);

    assertThatThrownBy(() -> check().requireNotRevoked(fixture.authenticationCertificate))
        .isInstanceOf(SmartIdCertificateStatusUnavailableException.class);
  }
}
