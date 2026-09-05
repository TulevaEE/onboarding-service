package ee.tuleva.onboarding.signature.smartid;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.principal.AuthenticatedPerson.SMART_ID_DOCUMENT_NUMBER;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.demoProperties;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.demoTestAccountSigningCertificate;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.documentNumber;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.runningStatus;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.sk.smartid.CertificateChoiceResponse;
import ee.sk.smartid.CertificateChoiceResponseValidator;
import ee.sk.smartid.CertificateLevel;
import ee.sk.smartid.CertificateParser;
import ee.sk.smartid.SignatureResponse;
import ee.sk.smartid.SignatureResponseValidator;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.exception.permanent.SmartIdClientException;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.CertificateInfo;
import ee.sk.smartid.rest.dao.CertificateResponse;
import ee.sk.smartid.rest.dao.NotificationCertificateChoiceSessionResponse;
import ee.sk.smartid.rest.dao.NotificationSignatureSessionRequest;
import ee.sk.smartid.rest.dao.NotificationSignatureSessionResponse;
import ee.sk.smartid.rest.dao.SessionStatus;
import ee.sk.smartid.rest.dao.VerificationCode;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.signature.DigiDocFacade;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.signature.SmartIdSignatureSession;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;
import org.junit.jupiter.api.Test;

class SmartIdSignerTest {

  private static final String CERTIFICATE_SESSION_ID = "certificate-session-id";
  private static final String SIGNING_SESSION_ID = "signing-session-id";

  private final SmartIdConnector connector = mock(SmartIdConnector.class);
  private final CertificateChoiceResponseValidator certificateChoiceResponseValidator =
      mock(CertificateChoiceResponseValidator.class);
  private final SignatureResponseValidator signatureResponseValidator =
      mock(SignatureResponseValidator.class);
  private final GenericSessionStore sessionStore = mock(GenericSessionStore.class);
  private final DigiDocFacade digiDocFacade = mock(DigiDocFacade.class);
  private final DataToSign dataToSign = mock(DataToSign.class);
  private final Container container = mock(Container.class);
  private final SmartIdSigner signer =
      new SmartIdSigner(
          smartIdClient(),
          connector,
          certificateChoiceResponseValidator,
          signatureResponseValidator,
          sessionStore,
          digiDocFacade);

  private final List<SignatureFile> files =
      List.of(new SignatureFile("test.txt", "text/plain", "Test".getBytes(UTF_8)));
  private final byte[] dataToSignBytes = "data to sign".getBytes(UTF_8);
  private final X509Certificate certificate =
      CertificateParser.parseX509Certificate(demoTestAccountSigningCertificate);

  private SmartIdClient smartIdClient() {
    var client = new SmartIdClient();
    client.setSmartIdConnector(connector);
    client.setRelyingPartyUUID(demoProperties.relyingPartyUUID());
    client.setRelyingPartyName(demoProperties.relyingPartyName());
    return client;
  }

  private static AuthenticatedPerson signerWithDocumentNumber() {
    return sampleAuthenticatedPersonAndMember()
        .attributes(Map.of(SMART_ID_DOCUMENT_NUMBER, documentNumber))
        .build();
  }

  private static AuthenticatedPerson signerWithoutDocumentNumber() {
    return sampleAuthenticatedPersonAndMember().build();
  }

  private void givenTheSigningCertificateIsOnFile() {
    given(connector.getCertificateByDocumentNumber(eq(documentNumber), any()))
        .willReturn(
            new CertificateResponse(
                "OK", new CertificateInfo(demoTestAccountSigningCertificate, "QUALIFIED")));
  }

  private void givenContainerIsBuilt() {
    given(digiDocFacade.buildContainer(files)).willReturn(container);
    given(digiDocFacade.dataToSign(container, certificate)).willReturn(dataToSign);
    given(dataToSign.getDataToSign()).willReturn(dataToSignBytes);
  }

  private void givenSigningSessionStarts() {
    given(
            connector.initNotificationSignature(
                any(NotificationSignatureSessionRequest.class), eq(documentNumber)))
        .willReturn(
            new NotificationSignatureSessionResponse(
                SIGNING_SESSION_ID, new VerificationCode("numeric4", "4321")));
  }

  @SneakyThrows
  private static String sha256Base64(byte[] data) {
    return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(data));
  }

  private static SessionStatus completeStatus() {
    var status = new SessionStatus();
    status.setState("COMPLETE");
    return status;
  }

  @Test
  void startSignWithAKnownDocumentNumberFetchesTheCertificateSilentlyAndStartsSigning() {
    givenTheSigningCertificateIsOnFile();
    givenContainerIsBuilt();
    givenSigningSessionStarts();

    SmartIdSignatureSession session = signer.startSign(files, signerWithDocumentNumber());

    assertThat(session.getPersonalCode()).isEqualTo(signerWithDocumentNumber().getPersonalCode());
    assertThat(session.getFiles()).isEqualTo(files);
    assertThat(session.getCertificateSessionId()).isNull();
    assertThat(session.getDocumentNumber()).isEqualTo(documentNumber);
    assertThat(session.getSigningSessionId()).isEqualTo(SIGNING_SESSION_ID);
    assertThat(session.getVerificationCode()).isEqualTo("4321");
    assertThat(session.getDataToSign()).isSameAs(dataToSign);
    assertThat(session.getContainer()).isSameAs(container);
    verify(connector, never()).initNotificationCertificateChoice(any(), any());
  }

  @Test
  void signingRequestsTheLegacyRsaAlgorithmOverTheSha256DigestForDigiDoc4j() {
    givenTheSigningCertificateIsOnFile();
    givenContainerIsBuilt();
    givenSigningSessionStarts();

    signer.startSign(files, signerWithDocumentNumber());

    verify(connector)
        .initNotificationSignature(
            argThat(
                request ->
                    "sha256WithRSAEncryption"
                            .equals(request.signatureProtocolParameters().signatureAlgorithm())
                        && request.signatureProtocolParameters().signatureAlgorithmParameters()
                            == null
                        && sha256Base64(dataToSignBytes)
                            .equals(request.signatureProtocolParameters().digest())
                        && "QUALIFIED".equals(request.certificateLevel())
                        && "RAW_DIGEST_SIGNATURE".equals(request.signatureProtocol())),
            eq(documentNumber));
  }

  @Test
  void startSignWithoutADocumentNumberStartsACertificateChoiceForThePersonalCode() {
    AuthenticatedPerson person = signerWithoutDocumentNumber();
    given(
            connector.initNotificationCertificateChoice(
                any(),
                argThat(
                    identifier ->
                        identifier.getIdentifier().equals("PNOEE-" + person.getPersonalCode()))))
        .willReturn(new NotificationCertificateChoiceSessionResponse(CERTIFICATE_SESSION_ID));

    SmartIdSignatureSession session = signer.startSign(files, person);

    assertThat(session.getCertificateSessionId()).isEqualTo(CERTIFICATE_SESSION_ID);
    assertThat(session.getSigningSessionId()).isNull();
    assertThat(session.getVerificationCode()).isNull();
    verify(connector, never()).getCertificateByDocumentNumber(any(), any());
  }

  @Test
  void getSignedFileReturnsNothingWhileTheCertificateChoiceIsRunning() {
    var session = new SmartIdSignatureSession("38888888888", files);
    session.setCertificateSessionId(CERTIFICATE_SESSION_ID);
    given(connector.getSessionStatus(CERTIFICATE_SESSION_ID)).willReturn(runningStatus());

    assertThat(signer.getSignedFile(session)).isNull();
    verify(sessionStore, never()).save(any());
  }

  @Test
  void getSignedFileStartsSigningOnceTheCertificateIsChosen() {
    var session = new SmartIdSignatureSession("38888888888", files);
    session.setCertificateSessionId(CERTIFICATE_SESSION_ID);
    var certificateStatus = completeStatus();
    var choice = new CertificateChoiceResponse();
    choice.setCertificate(certificate);
    choice.setDocumentNumber(documentNumber);
    given(connector.getSessionStatus(CERTIFICATE_SESSION_ID)).willReturn(certificateStatus);
    given(
            certificateChoiceResponseValidator.validate(
                certificateStatus, CertificateLevel.QUALIFIED))
        .willReturn(choice);
    givenContainerIsBuilt();
    givenSigningSessionStarts();

    byte[] signedFile = signer.getSignedFile(session);

    assertThat(signedFile).isNull();
    assertThat(session.getSigningSessionId()).isEqualTo(SIGNING_SESSION_ID);
    assertThat(session.getVerificationCode()).isEqualTo("4321");
    assertThat(session.getDocumentNumber()).isEqualTo(documentNumber);
    assertThat(session.getDataToSign()).isSameAs(dataToSign);
    assertThat(session.getContainer()).isSameAs(container);
    verify(sessionStore).save(session);
  }

  @Test
  void getSignedFileReturnsNothingWhileSigningIsRunning() {
    var session = signingSession();
    given(connector.getSessionStatus(SIGNING_SESSION_ID)).willReturn(runningStatus());

    assertThat(signer.getSignedFile(session)).isNull();
  }

  @Test
  void getSignedFileRejectsAnUnknownSessionState() {
    var session = signingSession();
    var status = new SessionStatus();
    status.setState("UNKNOWN");
    given(connector.getSessionStatus(SIGNING_SESSION_ID)).willReturn(status);

    assertThatThrownBy(() -> signer.getSignedFile(session))
        .isInstanceOf(SmartIdClientException.class);
  }

  @Test
  void getSignedFileFinalizesTheContainerWithTheValidatedSignature() {
    var session = signingSession();
    var signingStatus = completeStatus();
    var signature = new SignatureResponse();
    signature.setSignatureValueInBase64(
        Base64.getEncoder().encodeToString("signature".getBytes(UTF_8)));
    given(connector.getSessionStatus(SIGNING_SESSION_ID)).willReturn(signingStatus);
    given(signatureResponseValidator.validate(signingStatus, CertificateLevel.QUALIFIED))
        .willReturn(signature);
    given(digiDocFacade.addSignatureToContainer("signature".getBytes(UTF_8), dataToSign, container))
        .willReturn("signed container".getBytes(UTF_8));

    byte[] signedFile = signer.getSignedFile(session);

    assertThat(signedFile).isEqualTo("signed container".getBytes(UTF_8));
  }

  private SmartIdSignatureSession signingSession() {
    var session = new SmartIdSignatureSession("38888888888", files);
    session.setDocumentNumber(documentNumber);
    session.setSigningSessionId(SIGNING_SESSION_ID);
    session.setVerificationCode("4321");
    session.setDataToSign(dataToSign);
    session.setContainer(container);
    return session;
  }
}
