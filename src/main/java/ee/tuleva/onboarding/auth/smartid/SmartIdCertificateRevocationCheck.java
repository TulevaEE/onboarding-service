package ee.tuleva.onboarding.auth.smartid;

import static org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers.id_pkix_ocsp_nonce;

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
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SmartIdCertificateRevocationCheck {

  private static final MediaType OCSP_REQUEST =
      MediaType.parseMediaType("application/ocsp-request");
  private static final MediaType OCSP_RESPONSE =
      MediaType.parseMediaType("application/ocsp-response");
  private static final Duration CLOCK_SKEW_TOLERATED_BETWEEN_US_AND_THE_RESPONDER =
      Duration.ofMinutes(15);
  private static final int NONCE_BYTES = 32;

  private final List<X509Certificate> issuingCaCertificates;
  private final RestClient restClient;
  private final OCSPUtils ocspUtils;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public SmartIdCertificateRevocationCheck(
      TrustedCACertStore smartIdTrustedCaCertStore,
      @Qualifier("smartIdOcspRestClient") RestClient restClient,
      OCSPUtils ocspUtils,
      Clock clock) {
    this.issuingCaCertificates = issuingCaCertificatesOf(smartIdTrustedCaCertStore);
    this.restClient = restClient;
    this.ocspUtils = ocspUtils;
    this.clock = clock;
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
    CertificateStatus status = verifiedStatus(response, certificateId, nonce, issuer);
    if (status != CertificateStatus.GOOD) {
      throw new SmartIdCertificateRevokedException(
          status instanceof RevokedStatus ? "REVOKED" : "UNKNOWN");
    }
  }

  private X509Certificate issuerOf(X509Certificate certificate) {
    return issuingCaCertificates.stream()
        .filter(ca -> ca.getSubjectX500Principal().equals(certificate.getIssuerX500Principal()))
        .filter(ca -> signedBy(certificate, ca))
        .findFirst()
        .orElseThrow(
            () ->
                new SmartIdCertificateStatusUnavailableException(
                    "no bundled CA issued the certificate"));
  }

  private static boolean signedBy(X509Certificate certificate, X509Certificate ca) {
    try {
      certificate.verify(ca.getPublicKey());
      return true;
    } catch (GeneralSecurityException e) {
      return false;
    }
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
        throw new SmartIdCertificateStatusUnavailableException("empty response");
      }
      return response;
    } catch (RestClientException e) {
      throw new SmartIdCertificateStatusUnavailableException("responder unreachable", e);
    }
  }

  private CertificateStatus verifiedStatus(
      byte[] encodedResponse,
      CertificateID certificateId,
      Extension nonce,
      X509Certificate issuer) {
    try {
      BasicOCSPResp response = basicResponse(encodedResponse);
      requireSignedByAnAuthorisedResponder(response, issuer);
      requireEchoed(nonce, response.getExtension(id_pkix_ocsp_nonce));
      SingleResp answer = onlyAnswerAbout(certificateId, response);
      requireFresh(answer);
      return answer.getCertStatus();
    } catch (IOException | OCSPException | GeneralSecurityException | OperatorCreationException e) {
      throw new SmartIdCertificateStatusUnavailableException("unreadable response", e);
    }
  }

  private static BasicOCSPResp basicResponse(byte[] encodedResponse)
      throws IOException, OCSPException {
    OCSPResp response = new OCSPResp(encodedResponse);
    if (response.getStatus() != OCSPResp.SUCCESSFUL) {
      throw new SmartIdCertificateStatusUnavailableException(
          "responder status " + response.getStatus());
    }
    if (!(response.getResponseObject() instanceof BasicOCSPResp basic)) {
      throw new SmartIdCertificateStatusUnavailableException("not a basic OCSP response");
    }
    return basic;
  }

  private void requireSignedByAnAuthorisedResponder(BasicOCSPResp response, X509Certificate issuer)
      throws GeneralSecurityException, OperatorCreationException, OCSPException {
    X509Certificate responder = responderCertificate(response, issuer);
    if (!response.isSignatureValid(new JcaContentVerifierProviderBuilder().build(responder))) {
      throw new SmartIdCertificateStatusUnavailableException("response signature invalid");
    }
  }

  private X509Certificate responderCertificate(BasicOCSPResp response, X509Certificate issuer)
      throws GeneralSecurityException {
    X509CertificateHolder[] certificates = response.getCerts();
    if (certificates.length == 0) {
      return issuer;
    }
    X509Certificate responder = new JcaX509CertificateConverter().getCertificate(certificates[0]);
    if (responder.equals(issuer)) {
      return issuer;
    }
    if (!signedBy(responder, issuer)) {
      throw new SmartIdCertificateStatusUnavailableException(
          "responder certificate not issued by the certificate's CA");
    }
    List<String> extendedKeyUsage = responder.getExtendedKeyUsage();
    if (extendedKeyUsage == null
        || !extendedKeyUsage.contains(KeyPurposeId.id_kp_OCSPSigning.getId())) {
      throw new SmartIdCertificateStatusUnavailableException(
          "responder certificate not authorised for OCSP signing");
    }
    responder.checkValidity(Date.from(Instant.now(clock)));
    return responder;
  }

  private static void requireEchoed(Extension sent, @Nullable Extension received) {
    if (received == null
        || !Arrays.equals(sent.getExtnValue().getOctets(), received.getExtnValue().getOctets())) {
      throw new SmartIdCertificateStatusUnavailableException("nonce not echoed");
    }
  }

  private static SingleResp onlyAnswerAbout(CertificateID certificateId, BasicOCSPResp response) {
    SingleResp[] answers = response.getResponses();
    if (answers.length != 1 || !certificateId.equals(answers[0].getCertID())) {
      throw new SmartIdCertificateStatusUnavailableException(
          "response is not about the certificate asked for");
    }
    return answers[0];
  }

  private void requireFresh(SingleResp answer) {
    Instant now = Instant.now(clock);
    Instant thisUpdate = answer.getThisUpdate().toInstant();
    if (thisUpdate.isBefore(now.minus(CLOCK_SKEW_TOLERATED_BETWEEN_US_AND_THE_RESPONDER))
        || thisUpdate.isAfter(now.plus(CLOCK_SKEW_TOLERATED_BETWEEN_US_AND_THE_RESPONDER))) {
      throw new SmartIdCertificateStatusUnavailableException("response not fresh");
    }
  }
}
