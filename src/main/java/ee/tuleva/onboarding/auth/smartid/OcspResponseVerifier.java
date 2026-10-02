package ee.tuleva.onboarding.auth.smartid;

import static org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers.id_pkix_ocsp_nonce;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.ocsp.ResponderID;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.jspecify.annotations.Nullable;

final class OcspResponseVerifier {

  private static final Duration CLOCK_SKEW_TOLERATED_BETWEEN_US_AND_THE_RESPONDER =
      Duration.ofMinutes(15);

  private final Clock clock;

  OcspResponseVerifier(Clock clock) {
    this.clock = clock;
  }

  CertificateStatus verifiedStatus(
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
    ResponderID responderId = response.getResponderId().toASN1Primitive();
    if (OcspResponderIds.identifies(responderId, new JcaX509CertificateHolder(issuer))) {
      return issuer;
    }
    X509Certificate responder = OcspResponderIds.includedCertificateOf(responderId, response);
    requireDelegatedBy(issuer, responder);
    return responder;
  }

  private void requireDelegatedBy(X509Certificate issuer, X509Certificate responder)
      throws GeneralSecurityException {
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

  static boolean signedBy(X509Certificate certificate, X509Certificate ca) {
    try {
      certificate.verify(ca.getPublicKey());
      return true;
    } catch (GeneralSecurityException e) {
      return false;
    }
  }
}
