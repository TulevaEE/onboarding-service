package ee.tuleva.onboarding.signature.mobileid;

import static ee.sk.mid.MidDisplayTextFormat.GSM7;
import static ee.sk.mid.MidHashType.SHA256;
import static ee.sk.mid.MidLanguage.ENG;

import ee.sk.mid.MidClient;
import ee.sk.mid.MidHashToSign;
import ee.sk.mid.MidSignature;
import ee.sk.mid.exception.MidInternalErrorException;
import ee.sk.mid.rest.MidConnector;
import ee.sk.mid.rest.dao.MidSessionStatus;
import ee.sk.mid.rest.dao.request.MidCertificateRequest;
import ee.sk.mid.rest.dao.request.MidSessionStatusRequest;
import ee.sk.mid.rest.dao.request.MidSignatureRequest;
import ee.sk.mid.rest.dao.response.MidCertificateChoiceResponse;
import ee.sk.mid.rest.dao.response.MidSignatureResponse;
import ee.tuleva.onboarding.signature.DigiDocFacade;
import ee.tuleva.onboarding.signature.SignatureFile;
import java.security.cert.X509Certificate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MobileIdSigner {

  private final MidClient mobileIdClient;
  private final MidConnector mobileIdConnector;
  private final DigiDocFacade digiDocFacade;

  @Value("${mobile-id.pollingSleepTimeoutSeconds}")
  private int pollingSleepTimeoutSeconds;

  public MobileIdSignatureSession startSign(
      List<SignatureFile> files, String personalCode, String phoneNumber) {
    X509Certificate certificate = getCertificate(phoneNumber, personalCode);
    Container container = digiDocFacade.buildContainer(files);

    DataToSign dataToSign = digiDocFacade.dataToSign(container, certificate);
    byte[] dataToHash = dataToSign.getDataToSign();

    MidHashToSign hashToSign = hashToSign(dataToHash);

    String verificationCode = hashToSign.calculateVerificationCode();

    MidSignatureRequest request =
        MidSignatureRequest.newBuilder()
            .withPhoneNumber(phoneNumber)
            .withNationalIdentityNumber(personalCode)
            .withHashToSign(hashToSign)
            .withLanguage(ENG)
            .withDisplayText("Sign document?")
            .withDisplayTextFormat(GSM7)
            .build();

    MidSignatureResponse response = mobileIdConnector.sign(request);

    return new MobileIdSignatureSession(
        response.getSessionID(), verificationCode, dataToSign, container);
  }

  @Nullable
  public byte[] getSignedFile(MobileIdSignatureSession session) {
    MidSessionStatus sessionStatus = getSessionStatus(session.getSessionId());

    if (sessionStatus == null) {
      return null;
    }

    return finalizeSignature(session, sessionStatus);
  }

  private X509Certificate getCertificate(String phoneNumber, String nationalIdentityNumber) {
    MidCertificateRequest request =
        MidCertificateRequest.newBuilder()
            .withPhoneNumber(phoneNumber)
            .withNationalIdentityNumber(nationalIdentityNumber)
            .build();

    MidCertificateChoiceResponse response = mobileIdConnector.getCertificate(request);

    return mobileIdClient.createMobileIdCertificate(response);
  }

  private MidHashToSign hashToSign(byte[] data) {
    return MidHashToSign.newBuilder().withDataToHash(data).withHashType(SHA256).build();
  }

  @Nullable
  private MidSessionStatus getSessionStatus(String sessionId) {
    MidSessionStatusRequest request =
        new MidSessionStatusRequest(sessionId, pollingSleepTimeoutSeconds);
    MidSessionStatus sessionStatus =
        mobileIdConnector.getSessionStatus(request, "/signature/session/{sessionId}");
    if (sessionStatus == null || "RUNNING".equalsIgnoreCase(sessionStatus.getState())) {
      return null;
    }
    if (!"COMPLETE".equalsIgnoreCase(sessionStatus.getState())) {
      throw new MidInternalErrorException(
          "Invalid Mobile-ID session status: " + sessionStatus.getState());
    }
    return sessionStatus;
  }

  @SneakyThrows
  private byte[] finalizeSignature(
      MobileIdSignatureSession session, MidSessionStatus sessionStatus) {
    MidSignature mobileIdSignature = mobileIdClient.createMobileIdSignature(sessionStatus);
    return digiDocFacade.addSignatureToContainer(
        mobileIdSignature.getValue(), session.getDataToSign(), session.getContainer());
  }
}
