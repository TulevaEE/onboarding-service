package ee.tuleva.onboarding.mandate.signature.mobileid;

import static ee.sk.mid.MidLanguage.ENG;

import com.codeborne.security.mobileid.SignatureFile;
import ee.sk.mid.MidClient;
import ee.sk.mid.MidHashToSign;
import ee.sk.mid.MidHashType;
import ee.sk.mid.MidSignature;
import ee.sk.mid.exception.MidDeliveryException;
import ee.sk.mid.exception.MidInternalErrorException;
import ee.sk.mid.exception.MidInvalidUserConfigurationException;
import ee.sk.mid.exception.MidMissingOrInvalidParameterException;
import ee.sk.mid.exception.MidNotMidClientException;
import ee.sk.mid.exception.MidPhoneNotAvailableException;
import ee.sk.mid.exception.MidSessionNotFoundException;
import ee.sk.mid.exception.MidSessionTimeoutException;
import ee.sk.mid.exception.MidUnauthorizedException;
import ee.sk.mid.exception.MidUserCancellationException;
import ee.sk.mid.rest.MidConnector;
import ee.sk.mid.rest.MidSessionStatusPoller;
import ee.sk.mid.rest.dao.MidSessionStatus;
import ee.sk.mid.rest.dao.request.MidSignatureRequest;
import ee.sk.mid.rest.dao.response.MidSignatureResponse;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.DataFile;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureBuilder;
import org.digidoc4j.SignatureProfile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class MobileIdSignatureService {
  private final MobileIdCertificateService certificateService;
  private final MidConnector connector;
  private final Configuration configuration;
  private final MidClient client;
  private final MidSessionStatusPoller poller;

  public MobileIdSignatureSession startSign(
      List<SignatureFile> files, String personalCode, String phoneNumber) {
    Container container = ContainerBuilder.aContainer().withConfiguration(configuration).build();

    files.forEach(
        file -> container.addDataFile(new DataFile(file.content, file.name, file.mimeType)));

    X509Certificate signingCert = certificateService.getCertificate(personalCode, phoneNumber);

    DataToSign dataToSignExternally =
        SignatureBuilder.aSignature(container)
            .withSigningCertificate(signingCert)
            .withSignatureDigestAlgorithm(DigestAlgorithm.SHA256)
            .withSignatureProfile(SignatureProfile.LT)
            .buildDataToSign();

    MidHashToSign hashToSign =
        MidHashToSign.newBuilder()
            .withDataToHash(dataToSignExternally.getDataToSign())
            .withHashType(MidHashType.SHA256)
            .build();

    MidSignatureRequest signatureRequest =
        MidSignatureRequest.newBuilder()
            .withPhoneNumber(phoneNumber)
            .withNationalIdentityNumber(personalCode)
            .withHashToSign(hashToSign)
            .withLanguage(ENG)
            .build();

    MidSignatureResponse response = connector.sign(signatureRequest);

    return MobileIdSignatureSession.newBuilder()
        .withSessionID(response.getSessionID())
        .withVerificationCode(hashToSign.calculateVerificationCode())
        .withDataToSign(dataToSignExternally)
        .withContainer(container)
        .build();
  }

  protected Signature getSignedSignature(DataToSign dataToSign, MidSignature mobileIdSignature) {
    return dataToSign.finalize(mobileIdSignature.getValue());
  }

  public byte[] getSignedFile(MobileIdSignatureSession signingSessionInfo) {
    Signature signature;
    byte[] signedFile = null;
    try {
      MidSessionStatus sessionStatus =
          poller.fetchFinalSignatureSessionStatus(signingSessionInfo.getSessionID());

      MidSignature mobileIdSignature = client.createMobileIdSignature(sessionStatus);
      DataToSign dataToSign = signingSessionInfo.getDataToSign();

      signingSessionInfo
          .getContainer()
          .addSignature(getSignedSignature(dataToSign, mobileIdSignature));
      signedFile = IOUtils.toByteArray(signingSessionInfo.getContainer().saveAsStream());
    } catch (MidUserCancellationException e) {
      String errorMessage = "User cancelled operation from his/her phone.";
      log.info(errorMessage, e);
      signingSessionInfo.setErrors(Collections.singletonList(errorMessage));
    } catch (MidNotMidClientException e) {
      String errorMessage = "User is not a MID client or user's certificates are revoked";
      log.info(errorMessage, e);
      signingSessionInfo.setErrors(Collections.singletonList(errorMessage));
    } catch (MidSessionTimeoutException e) {
      String errorMessage = "User did not type in PIN code or communication error.";
      log.info(errorMessage, e);
      signingSessionInfo.setErrors(Collections.singletonList(errorMessage));
    } catch (MidPhoneNotAvailableException e) {
      String errorMessage =
          "Unable to reach phone/SIM card. User needs to check if phone has coverage.";
      log.info(errorMessage, e);
      signingSessionInfo.setErrors(Collections.singletonList(errorMessage));
    } catch (MidDeliveryException e) {
      String errorMessage = "Error communicating with the phone/SIM card.";
      log.info(errorMessage, e);
      signingSessionInfo.setErrors(Collections.singletonList(errorMessage));
    } catch (MidInvalidUserConfigurationException e) {
      String errorMessage =
          "Mobile-ID configuration on user's SIM card differs from what is configured on service provider side. User needs to contact his/her mobile operator.";
      log.info(errorMessage, e);
      signingSessionInfo.setErrors(Collections.singletonList(errorMessage));
    } catch (MidSessionNotFoundException
        | MidMissingOrInvalidParameterException
        | MidUnauthorizedException e) {
      String errorMessage = "Integrator-side error with MID integration or configuration";
      log.info(errorMessage, e);
      signingSessionInfo.setErrors(Collections.singletonList(errorMessage));
    } catch (MidInternalErrorException e) {
      String errorMessage = "MID service returned internal error that cannot be handled locally.";
      log.info(errorMessage, e);
      signingSessionInfo.setErrors(Collections.singletonList(errorMessage));
    } catch (IOException e) {
      String errorMessage = "Could not create container file.";
      log.info(errorMessage, e);
      signingSessionInfo.setErrors(Collections.singletonList(errorMessage));
    }

    if (!signingSessionInfo.getErrors().isEmpty()) {
      throw new IllegalStateException(String.join(",", signingSessionInfo.getErrors()));
    }

    return signedFile;
  }
}
