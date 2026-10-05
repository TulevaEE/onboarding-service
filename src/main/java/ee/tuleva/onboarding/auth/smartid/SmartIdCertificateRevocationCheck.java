package ee.tuleva.onboarding.auth.smartid;

import static org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers.id_pkix_ocsp_nonce;
import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

import ee.sk.smartid.TrustedCACertStore;
import ee.tuleva.onboarding.auth.ocsp.OCSPUtils;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Slf4j
public class SmartIdCertificateRevocationCheck {

  private static final MediaType OCSP_REQUEST =
      MediaType.parseMediaType("application/ocsp-request");
  private static final MediaType OCSP_RESPONSE =
      MediaType.parseMediaType("application/ocsp-response");
  private static final int NONCE_BYTES = 32;
  private static final int RETRIES_WHILE_THE_PERSON_WAITS = 2;
  private static final Duration DELAY_BEFORE_ASKING_AGAIN = Duration.ofMillis(200);
  private static final Duration RETRY_WINDOW_NO_LONGER_THAN_ONE_OCSP_READ_TIMEOUT =
      Duration.ofSeconds(5);

  private final List<X509Certificate> issuingCaCertificates;
  private final RestClient restClient;
  private final OCSPUtils ocspUtils;
  private final OcspResponseVerifier responseVerifier;
  private final RetryTemplate retryTemplate;
  private final SecureRandom random = new SecureRandom();

  @Autowired
  public SmartIdCertificateRevocationCheck(
      TrustedCACertStore smartIdTrustedCaCertStore,
      @Qualifier("smartIdOcspRestClient") RestClient restClient,
      OCSPUtils ocspUtils,
      Clock clock) {
    this(
        smartIdTrustedCaCertStore,
        restClient,
        ocspUtils,
        clock,
        DELAY_BEFORE_ASKING_AGAIN,
        RETRY_WINDOW_NO_LONGER_THAN_ONE_OCSP_READ_TIMEOUT);
  }

  SmartIdCertificateRevocationCheck(
      TrustedCACertStore smartIdTrustedCaCertStore,
      RestClient restClient,
      OCSPUtils ocspUtils,
      Clock clock,
      Duration retryDelay,
      Duration retryWindow) {
    this.issuingCaCertificates = issuingCaCertificatesOf(smartIdTrustedCaCertStore);
    this.restClient = restClient;
    this.ocspUtils = ocspUtils;
    this.responseVerifier = new OcspResponseVerifier(clock);
    this.retryTemplate = retrying(retryDelay, retryWindow);
  }

  private static RetryTemplate retrying(Duration delay, Duration window) {
    var retryTemplate =
        new RetryTemplate(
            RetryPolicy.builder()
                .includes(RestClientException.class, EmptyOcspResponseException.class)
                .excludes(HttpClientErrorException.class)
                .maxRetries(RETRIES_WHILE_THE_PERSON_WAITS)
                .delay(delay)
                .timeout(window)
                .build());
    retryTemplate.setRetryListener(new LoggingEachRetry());
    return retryTemplate;
  }

  private static List<X509Certificate> issuingCaCertificatesOf(TrustedCACertStore store) {
    return Stream.concat(
            store.getTrustAnchors().stream().map(TrustAnchor::getTrustedCert),
            store.getTrustedCACertificates().stream())
        .toList();
  }

  public void requireNotRevoked(X509Certificate certificate) {
    X509Certificate issuer = issuerOf(certificate);
    CertificateID certificateId = certificateId(issuer, certificate);
    Extension nonce = nonce();
    byte[] response = ask(responderOf(certificate), request(certificateId, nonce));
    CertificateStatus status =
        responseVerifier.verifiedStatus(response, certificateId, nonce, issuer);
    if (status != CertificateStatus.GOOD) {
      throw new SmartIdCertificateRevokedException(
          status instanceof RevokedStatus ? "REVOKED" : "UNKNOWN");
    }
  }

  private X509Certificate issuerOf(X509Certificate certificate) {
    return issuingCaCertificates.stream()
        .filter(ca -> ca.getSubjectX500Principal().equals(certificate.getIssuerX500Principal()))
        .filter(ca -> OcspResponseVerifier.signedBy(certificate, ca))
        .findFirst()
        .orElseThrow(
            () ->
                new SmartIdCertificateStatusUnavailableException(
                    "no bundled CA issued the certificate"));
  }

  private URI responderOf(X509Certificate certificate) {
    try {
      return ocspUtils.getResponderURI(certificate);
    } catch (RuntimeException e) {
      throw new SmartIdCertificateStatusUnavailableException("no OCSP responder in the AIA", e);
    }
  }

  private static CertificateID certificateId(X509Certificate issuer, X509Certificate certificate) {
    try {
      return new CertificateID(
          new JcaDigestCalculatorProviderBuilder().build().get(CertificateID.HASH_SHA1),
          new JcaX509CertificateHolder(issuer),
          certificate.getSerialNumber());
    } catch (OperatorCreationException | GeneralSecurityException | OCSPException e) {
      throw new SmartIdCertificateStatusUnavailableException("cannot build the request", e);
    }
  }

  private Extension nonce() {
    byte[] value = new byte[NONCE_BYTES];
    random.nextBytes(value);
    try {
      return Extension.create(id_pkix_ocsp_nonce, false, new DEROctetString(value));
    } catch (IOException e) {
      throw new SmartIdCertificateStatusUnavailableException("cannot build the nonce", e);
    }
  }

  private static byte[] request(CertificateID certificateId, Extension nonce) {
    try {
      OCSPReq request =
          new OCSPReqBuilder()
              .addRequest(certificateId)
              .setRequestExtensions(new Extensions(nonce))
              .build();
      return request.getEncoded();
    } catch (OCSPException | IOException e) {
      throw new SmartIdCertificateStatusUnavailableException("cannot build the request", e);
    }
  }

  private byte[] ask(URI responder, byte[] request) {
    try {
      return retryTemplate.invoke(() -> post(responder, request));
    } catch (EmptyOcspResponseException e) {
      throw new SmartIdCertificateStatusUnavailableException("empty response");
    } catch (RestClientException e) {
      throw new SmartIdCertificateStatusUnavailableException("responder unreachable", e);
    }
  }

  private byte[] post(URI responder, byte[] request) {
    byte[] response =
        restClient
            .post()
            .uri(responder)
            .contentType(OCSP_REQUEST)
            .accept(OCSP_RESPONSE)
            .body(request)
            .retrieve()
            .body(byte[].class);
    if (response == null) {
      throw new EmptyOcspResponseException();
    }
    return response;
  }

  private static class EmptyOcspResponseException extends RuntimeException {}

  private static class LoggingEachRetry implements RetryListener {
    @Override
    public void beforeRetry(
        RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
      log.warn(
          "Smart-ID OCSP responder failed, asking again: attempt={}, reason={}",
          retryState.getRetryCount() + 1,
          getMostSpecificCause(retryState.getLastException()).getClass().getSimpleName());
    }
  }
}
