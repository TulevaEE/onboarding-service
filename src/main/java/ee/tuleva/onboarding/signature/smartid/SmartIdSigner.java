package ee.tuleva.onboarding.signature.smartid;

import static ee.sk.smartid.CertificateLevel.QUALIFIED;
import static ee.sk.smartid.signature.SigningSignatureAlgorithm.SHA256_WITH_RSA_ENCRYPTION;
import static java.util.Objects.requireNonNull;

import ee.sk.smartid.CertificateChoiceResponse;
import ee.sk.smartid.CertificateChoiceResponseValidator;
import ee.sk.smartid.SignatureResponse;
import ee.sk.smartid.SignatureResponseValidator;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.common.notification.interactions.NotificationInteraction;
import ee.sk.smartid.exception.permanent.SmartIdClientException;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.rest.dao.SemanticsIdentifier.CountryCode;
import ee.sk.smartid.rest.dao.SemanticsIdentifier.IdentityType;
import ee.sk.smartid.rest.dao.SessionStatus;
import ee.sk.smartid.signature.SignableData;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.signature.DigiDocFacade;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.signature.SmartIdSignatureSession;
import java.security.cert.X509Certificate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmartIdSigner {

  private static final String SIGNING_PROMPT = "Tuleva: Sign Document";

  private final SmartIdClient smartIdClient;
  private final SmartIdConnector smartIdConnector;
  private final CertificateChoiceResponseValidator certificateChoiceResponseValidator;
  private final SignatureResponseValidator signatureResponseValidator;
  private final GenericSessionStore sessionStore;
  private final DigiDocFacade digiDocFacade;

  public SmartIdSignatureSession startSign(List<SignatureFile> files, AuthenticatedPerson signer) {
    var session = new SmartIdSignatureSession(signer.getPersonalCode(), files);
    String documentNumber = signer.getSmartIdDocumentNumber().orElse(null);
    if (documentNumber != null) {
      startSigning(session, signingCertificate(documentNumber), documentNumber);
    } else {
      session.setCertificateSessionId(startCertificateChoice(signer.getPersonalCode()));
    }
    return session;
  }

  public byte @Nullable [] getSignedFile(SmartIdSignatureSession session) {
    if (session.getSigningSessionId() == null) {
      SessionStatus certificateStatus =
          completedStatus(requireNonNull(session.getCertificateSessionId()));
      if (certificateStatus == null) {
        return null;
      }
      CertificateChoiceResponse choice =
          certificateChoiceResponseValidator.validate(certificateStatus, QUALIFIED);
      startSigning(session, choice.getCertificate(), choice.getDocumentNumber());
      sessionStore.save(session);
      return null;
    }

    SessionStatus signingStatus = completedStatus(session.getSigningSessionId());
    if (signingStatus == null) {
      return null;
    }
    SignatureResponse signature = signatureResponseValidator.validate(signingStatus, QUALIFIED);
    return digiDocFacade.addSignatureToContainer(
        signature.getSignatureValue(),
        requireNonNull(session.getDataToSign()),
        requireNonNull(session.getContainer()));
  }

  private @Nullable SessionStatus completedStatus(String sessionId) {
    SessionStatus status = smartIdConnector.getSessionStatus(sessionId);
    if (status == null || "RUNNING".equalsIgnoreCase(status.getState())) {
      return null;
    }
    if (!"COMPLETE".equalsIgnoreCase(status.getState())) {
      throw new SmartIdClientException(
          "Invalid Smart-ID session status: state=" + status.getState());
    }
    return status;
  }

  private X509Certificate signingCertificate(String documentNumber) {
    return smartIdClient
        .createCertificateByDocumentNumber()
        .withDocumentNumber(documentNumber)
        .withCertificateLevel(QUALIFIED)
        .getCertificateByDocumentNumber()
        .certificate();
  }

  private String startCertificateChoice(String personalCode) {
    return smartIdClient
        .createNotificationCertificateChoice()
        .withSemanticsIdentifier(
            new SemanticsIdentifier(IdentityType.PNO, CountryCode.EE, personalCode))
        .withCertificateLevel(QUALIFIED)
        .initCertificateChoice()
        .sessionID();
  }

  private void startSigning(
      SmartIdSignatureSession session, X509Certificate certificate, String documentNumber) {
    Container container = digiDocFacade.buildContainer(session.getFiles());
    DataToSign dataToSign = digiDocFacade.dataToSign(container, certificate);
    var response =
        smartIdClient
            .createNotificationSignature()
            .withDocumentNumber(documentNumber)
            .withSignableData(
                new SignableData(
                    dataToSign.getDataToSign(),
                    SHA256_WITH_RSA_ENCRYPTION.getHashAlgorithmForLegacy()))
            .withSignatureAlgorithm(SHA256_WITH_RSA_ENCRYPTION)
            .withInteractions(List.of(NotificationInteraction.displayTextAndPin(SIGNING_PROMPT)))
            .withCertificateLevel(QUALIFIED)
            .initSignatureSession();
    session.setDocumentNumber(documentNumber);
    session.setSigningSessionId(response.sessionID());
    session.setVerificationCode(response.vc().value());
    session.setDataToSign(dataToSign);
    session.setContainer(container);
  }
}
