package ee.tuleva.onboarding.auth.ocsp;

import static org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers.id_pkix_ocsp_nonce;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.List;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class CertificateRevocationCheck {

  private static final MediaType OCSP_REQUEST =
      MediaType.parseMediaType("application/ocsp-request");
  private static final MediaType OCSP_RESPONSE =
      MediaType.parseMediaType("application/ocsp-response");
  private static final int NONCE_BYTES = 32;

  private final List<X509Certificate> issuingCaCertificates;
  private final RestClient restClient;
  private final OCSPUtils ocspUtils;
  private final OcspResponseVerifier responseVerifier;
  private final RetryTemplate retryTemplate;
  private final SecureRandom random = new SecureRandom();

  public CertificateRevocationCheck(
      List<X509Certificate> issuingCaCertificates,
      RestClient restClient,
      OCSPUtils ocspUtils,
      Clock clock,
      RetryTemplate retryTemplate) {
    this.issuingCaCertificates = issuingCaCertificates;
    this.restClient = restClient;
    this.ocspUtils = ocspUtils;
    this.responseVerifier = new OcspResponseVerifier(clock);
    this.retryTemplate = retryTemplate;
  }

  public void requireNotRevoked(X509Certificate certificate) {
    X509Certificate issuer = issuerOf(certificate);
    CertificateID certificateId = certificateId(issuer, certificate);
    Extension nonce = nonce();
    OCSPResp response = ask(responderOf(certificate), request(certificateId, nonce));
    CertificateStatus status =
        responseVerifier.verifiedStatus(response, certificateId, nonce, issuer);
    if (status != CertificateStatus.GOOD) {
      throw new CertificateRevokedException(
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
                new CertificateStatusUnavailableException("no bundled CA issued the certificate"));
  }

  private URI responderOf(X509Certificate certificate) {
    try {
      return ocspUtils.getResponderURI(certificate);
    } catch (RuntimeException e) {
      throw new CertificateStatusUnavailableException("no OCSP responder in the AIA", e);
    }
  }

  private static CertificateID certificateId(X509Certificate issuer, X509Certificate certificate) {
    try {
      return new CertificateID(
          new JcaDigestCalculatorProviderBuilder().build().get(CertificateID.HASH_SHA1),
          new JcaX509CertificateHolder(issuer),
          certificate.getSerialNumber());
    } catch (OperatorCreationException | GeneralSecurityException | OCSPException e) {
      throw new CertificateStatusUnavailableException("cannot build the request", e);
    }
  }

  private Extension nonce() {
    byte[] value = new byte[NONCE_BYTES];
    random.nextBytes(value);
    try {
      return Extension.create(id_pkix_ocsp_nonce, false, new DEROctetString(value));
    } catch (IOException e) {
      throw new CertificateStatusUnavailableException("cannot build the nonce", e);
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
      throw new CertificateStatusUnavailableException("cannot build the request", e);
    }
  }

  private OCSPResp ask(URI responder, byte[] request) {
    try {
      return retryTemplate.invoke(() -> post(responder, request));
    } catch (RestClientException e) {
      throw new CertificateStatusUnavailableException("responder unavailable", e);
    }
  }

  private OCSPResp post(URI responder, byte[] request) {
    byte[] body =
        restClient
            .post()
            .uri(responder)
            .contentType(OCSP_REQUEST)
            .accept(OCSP_RESPONSE)
            .body(request)
            .retrieve()
            .body(byte[].class);
    if (body == null) {
      throw new EmptyOcspResponseException();
    }
    OCSPResp response = parsed(body);
    if (OcspResponseVerifier.asksToBeAskedAgain(response)) {
      throw new ResponderAskedToTryAgainException();
    }
    return response;
  }

  private static OCSPResp parsed(byte[] body) {
    try {
      return new OCSPResp(body);
    } catch (IOException e) {
      throw new CertificateStatusUnavailableException("unreadable response", e);
    }
  }

  private static class EmptyOcspResponseException extends RestClientException {
    EmptyOcspResponseException() {
      super("Smart-ID OCSP responder answered empty");
    }
  }

  private static class ResponderAskedToTryAgainException extends RestClientException {
    ResponderAskedToTryAgainException() {
      super("Smart-ID OCSP responder asked to try again");
    }
  }
}
