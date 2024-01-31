package ee.tuleva.onboarding.auth.mobileid;

import static ee.tuleva.onboarding.error.response.ErrorsResponse.ofSingleError;

import ee.sk.mid.MidAuthentication;
import ee.sk.mid.MidAuthenticationHashToSign;
import ee.sk.mid.MidAuthenticationIdentity;
import ee.sk.mid.MidAuthenticationResponseValidator;
import ee.sk.mid.MidAuthenticationResult;
import ee.sk.mid.MidClient;
import ee.sk.mid.MidDisplayTextFormat;
import ee.sk.mid.MidLanguage;
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
import ee.sk.mid.rest.dao.request.MidAuthenticationRequest;
import ee.sk.mid.rest.dao.response.MidAuthenticationResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class MobileIdAuthService {
  private final MidClient client;
  private final MidAuthenticationResponseValidator validator;
  private final MidConnector connector;
  private final MidSessionStatusPoller poller;
  private final MobileNumberNormalizer normalizer;

  public MobileIDSession startLogin(String phoneNumber, String personalCode) {

    MidAuthenticationHashToSign authenticationHash =
        MidAuthenticationHashToSign.generateRandomHashOfDefaultType();

    String verificationCode = authenticationHash.calculateVerificationCode();

    MidAuthenticationRequest request =
        getBuildMidAuthenticationRequest(phoneNumber, personalCode, authenticationHash);
    MidAuthenticationResponse response = connector.authenticate(request);

    return new MobileIDSession(
        response.getSessionID(), verificationCode, authenticationHash, request.getPhoneNumber());
  }

  public boolean isLoginComplete(MobileIDSession session) {
    MidAuthenticationResult authenticationResult;

    try {
      MidSessionStatus sessionStatus =
          poller.fetchFinalAuthenticationSessionStatus(session.getSessionId());

      if (sessionStatus == null || "RUNNING".equalsIgnoreCase(sessionStatus.getState())) {
        return false;
      }

      if ("COMPLETE".equalsIgnoreCase(sessionStatus.getState())) {
        MidAuthentication authentication =
            client.createMobileIdAuthentication(sessionStatus, session.getAuthenticationHash());

        authenticationResult = validator.validate(authentication);

        if (!authenticationResult.isValid()) {
          throw MobileIdException.ofErrors(authenticationResult.getErrors());
        }

        MidAuthenticationIdentity authenticationIdentity =
            authenticationResult.getAuthenticationIdentity();

        session.updateSessionInfo(
            authenticationIdentity.getGivenName(),
            authenticationIdentity.getSurName(),
            authenticationIdentity.getIdentityCode());

        return authenticationResult.isValid();
      }

    } catch (MidUserCancellationException e) {
      throw new MobileIdException(
          ofSingleError("mobile.id.cancelled", "You cancelled operation from " + "your phone."));
    } catch (MidNotMidClientException e) {
      throw new MobileIdException(
          ofSingleError(
              "mobile.id.certificates.revoked",
              "You are not a Mobile-ID client or your Mobile-ID certificates are revoked. Please contact your mobile "
                  + "operator."));
    } catch (MidSessionTimeoutException e) {
      throw new MobileIdException(
          ofSingleError(
              "mobile.id.timeout",
              "You didn't type in PIN code into your phone or there was a communication error."));
    } catch (MidPhoneNotAvailableException e) {
      throw new MobileIdException(
          ofSingleError(
              "mobile.id.no.signal",
              "Unable to reach your phone. Please make sure your phone has mobile coverage."));
    } catch (MidDeliveryException e) {
      throw new MobileIdException(
          ofSingleError(
              "mobile.id.communication.error",
              "Communication error. Unable to " + "reach your phone."));
    } catch (MidInvalidUserConfigurationException e) {
      throw new MobileIdException(
          ofSingleError(
              "mobile.id.configuration.error",
              "Mobile-ID configuration on your SIM card differs from what is configured on service provider's side. "
                  + "Please contact your mobile operator."));
    } catch (MidSessionNotFoundException
        | MidMissingOrInvalidParameterException
        | MidUnauthorizedException e) {
      log.error(
          "Integrator-side error with MID integration (including insufficient input validation) or configuration",
          e);
      throw new MobileIdException(
          ofSingleError("mobile.id.error", "Client side error with mobile-ID integration."));
    } catch (MidInternalErrorException e) {
      log.warn("MID service returned internal error that cannot be handled locally.", e);
      throw new MobileIdException(ofSingleError("mobile.id.internal.error", "MID internal error"));
    }

    return false;
  }

  private MidAuthenticationRequest getBuildMidAuthenticationRequest(
      String phoneNumber, String personalCode, MidAuthenticationHashToSign authenticationHash) {
    return MidAuthenticationRequest.newBuilder()
        .withPhoneNumber(normalizer.normalizePhoneNumber(phoneNumber))
        .withNationalIdentityNumber(personalCode)
        .withHashToSign(authenticationHash)
        .withLanguage(MidLanguage.ENG)
        .withDisplayText("Log into self-service")
        .withDisplayTextFormat(MidDisplayTextFormat.GSM7)
        .build();
  }
}
