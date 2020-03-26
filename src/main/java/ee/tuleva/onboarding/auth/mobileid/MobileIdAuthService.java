package ee.tuleva.onboarding.auth.mobileid;

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
import ee.sk.mid.rest.dao.MidSessionStatus;
import ee.sk.mid.rest.dao.request.MidAuthenticationRequest;
import ee.sk.mid.rest.dao.response.MidAuthenticationResponse;
import ee.tuleva.onboarding.auth.exception.MidOperationException;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@AllArgsConstructor
public class MobileIdAuthService {
  private final MidClient client;
  private final MidAuthenticationResponseValidator validator;

  public MobileIDSession startLogin(String phoneNumber, String socialSecurityId) {

    MidAuthenticationHashToSign authenticationHash =
        MidAuthenticationHashToSign.generateRandomHashOfDefaultType();

    String verificationCode = authenticationHash.calculateVerificationCode();

    MidAuthenticationRequest request =
        getBuildMidAuthenticationRequest(phoneNumber, socialSecurityId, authenticationHash);
    MidConnector connector = client.getMobileIdConnector();
    MidAuthenticationResponse response = connector.authenticate(request);

    log.info(
        "Mobile ID authentication with challenge " + verificationCode + " sent to " + phoneNumber);

    return new MobileIDSession(
        response.getSessionID(), verificationCode, authenticationHash, request.getPhoneNumber());
  }

  public boolean isLoginComplete(MobileIDSession session) {
    MidAuthenticationResult authenticationResult;

    try {
      MidSessionStatus sessionStatus =
          client
              .getSessionStatusPoller()
              .fetchFinalAuthenticationSessionStatus(session.getSessionId());
      MidAuthentication authentication =
          client.createMobileIdAuthentication(sessionStatus, session.getAuthenticationHash());

      authenticationResult = validator.validate(authentication);

    } catch (MidUserCancellationException e) {
      log.info("User cancelled operation from his/her phone.");
      throw new MidOperationException("You cancelled operation from your phone.");
    } catch (MidNotMidClientException e) {
      log.info("User is not a MID client or user's certificates are revoked");
      throw new MidOperationException(
          "You are not a Mobile-ID client or your Mobile-ID certificates are revoked. Please contact your mobile operator.");
    } catch (MidSessionTimeoutException e) {
      log.info("User did not type in PIN code or communication error.");
      throw new MidOperationException(
          "You didn't type in PIN code into your phone or there was a communication error.");
    } catch (MidPhoneNotAvailableException e) {
      log.info("Unable to reach phone/SIM card. User needs to check if phone has coverage.");
      throw new MidOperationException(
          "Unable to reach your phone. Please make sure your phone has mobile coverage.");
    } catch (MidDeliveryException e) {
      log.info("Error communicating with the phone/SIM card.");
      throw new MidOperationException("Communication error. Unable to reach your phone.");
    } catch (MidInvalidUserConfigurationException e) {
      log.info(
          "Mobile-ID configuration on user's SIM card differs from what is configured on service provider side. User needs to contact his/her mobile operator.");
      throw new MidOperationException(
          "Mobile-ID configuration on your SIM card differs from what is configured on service provider's side. Please contact your mobile operator.");
    } catch (MidSessionNotFoundException
        | MidMissingOrInvalidParameterException
        | MidUnauthorizedException e) {
      log.error(
          "Integrator-side error with MID integration (including insufficient input validation) or configuration",
          e);
      throw new MidOperationException("Client side error with mobile-ID integration.", e);
    } catch (MidInternalErrorException e) {
      log.warn("MID service returned internal error that cannot be handled locally.");
      throw new MidOperationException("MID internal error", e);
    }

    if (!authenticationResult.isValid()) {
      throw new MidOperationException(authenticationResult.getErrors());
    }

    MidAuthenticationIdentity authenticationIdentity =
        authenticationResult.getAuthenticationIdentity();

    session.updateSessionInfo(
        authenticationIdentity.getGivenName(),
        authenticationIdentity.getSurName(),
        authenticationIdentity.getIdentityCode());

    return true;
  }

  private MidAuthenticationRequest getBuildMidAuthenticationRequest(
      String phoneNumber, String socialSecurityId, MidAuthenticationHashToSign authenticationHash) {
    return MidAuthenticationRequest.newBuilder()
        .withPhoneNumber(normalizePhoneNumber(phoneNumber))
        .withNationalIdentityNumber(socialSecurityId)
        .withHashToSign(authenticationHash)
        .withLanguage(MidLanguage.ENG)
        .withDisplayText("Log into self-service")
        .withDisplayTextFormat(MidDisplayTextFormat.GSM7)
        .build();
  }

  private String normalizePhoneNumber(String phone) {
    if (phone != null) {
      if (phone.startsWith("+")) phone = phone.substring(1);
      if (isLithuanian(phone)) {
        return "+370" + phone;
      } else if (isEstonian(phone)) {
        return "+372" + phone;
      }
    }
    return "+" + phone;
  }

  private boolean isEstonian(String phone) {
    return Stream.of("5", "81", "82", "83", "84", "870", "871").anyMatch(phone::startsWith);
  }

  private boolean isLithuanian(String phone) {
    return phone.startsWith("86");
  }
}
