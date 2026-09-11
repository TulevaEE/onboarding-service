package ee.tuleva.onboarding.auth.smartid;

import static java.nio.charset.StandardCharsets.UTF_8;

import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.SignatureProtocol;
import ee.sk.smartid.common.InteractionsMapper;
import ee.sk.smartid.common.devicelink.interactions.DeviceLinkInteraction;
import ee.sk.smartid.common.notification.interactions.NotificationInteraction;
import ee.sk.smartid.rest.dao.AcspV2SignatureProtocolParameters;
import ee.sk.smartid.rest.dao.DeviceLinkAuthenticationSessionRequest;
import ee.sk.smartid.rest.dao.DeviceLinkSessionResponse;
import ee.sk.smartid.rest.dao.NotificationAuthenticationSessionRequest;
import ee.sk.smartid.rest.dao.SessionCertificate;
import ee.sk.smartid.rest.dao.SessionMaskGenAlgorithm;
import ee.sk.smartid.rest.dao.SessionMaskGenAlgorithmParameters;
import ee.sk.smartid.rest.dao.SessionResult;
import ee.sk.smartid.rest.dao.SessionSignature;
import ee.sk.smartid.rest.dao.SessionSignatureAlgorithmParameters;
import ee.sk.smartid.rest.dao.SessionStatus;
import ee.sk.smartid.rest.dao.SignatureAlgorithmParameters;
import ee.sk.smartid.util.InteractionUtil;
import ee.tuleva.onboarding.auth.SmartIdProperties;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

public class SmartIdFixture {

  public static final String personalCode = "38501010002";
  public static final String firstName = "Aadu";
  public static final String lastName = "Kadakas";
  public static final String documentNumber = "PNOEE-38501010002-MOCK-Q";
  public static final String aSessionId = "someSessionId";
  public static final String aSessionToken = "session-token";
  public static final String aSessionSecret =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(UTF_8));
  public static final String aRpChallenge =
      Base64.getEncoder()
          .encodeToString(
              "rp-challenge-rp-challenge-rp-challenge-rp-challenge-1234".getBytes(UTF_8));
  public static final String aCallbackToken = "RrKjjT4aggzu27YBddX1bQ";
  public static final URI aDeviceLinkBase = URI.create("https://smart-id.com/device-link/");
  public static final String demoRelyingPartyUuid = "00000000-0000-4000-8000-000000000000";
  public static final String loginPrompt = "Log in to Tuleva?";

  public static final SmartIdProperties demoProperties =
      new SmartIdProperties(
          demoRelyingPartyUuid,
          "DEMO",
          "https://sid.demo.sk.ee/smart-id-rp/v3/",
          "smart-id-demo",
          "https://local.tuleva.ee/login/smart-id/callback",
          "classpath:smart-id/demo/*.pem");

  public static final SmartIdProperties liveProperties =
      new SmartIdProperties(
          "11111111-1111-4111-8111-111111111111",
          "Tuleva",
          "https://rp-api.smart-id.com/v3/",
          "smart-id",
          "https://pension.tuleva.ee/login/smart-id/callback",
          "classpath:smart-id/live/*.pem");

  public static final String demoTestAccountSigningCertificate =
      "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";

  public static AuthenticationIdentity anAuthenticationIdentity() {
    return anAuthenticationIdentity(firstName, lastName);
  }

  public static AuthenticationIdentity anAuthenticationIdentity(String givenName, String surname) {
    var identity = new AuthenticationIdentity();
    identity.setIdentityNumber(personalCode);
    identity.setGivenName(givenName);
    identity.setSurname(surname);
    identity.setCountry("EE");
    return identity;
  }

  public static SmartIdPerson aSmartIdPerson() {
    return new SmartIdPerson(anAuthenticationIdentity(), documentNumber);
  }

  public static RememberedSmartIdAccount aRememberedAccount() {
    return new RememberedSmartIdAccount(personalCode, documentNumber, firstName, lastName);
  }

  public static DeviceLinkSessionResponse aDeviceLinkSessionResponse(String sessionId) {
    return new DeviceLinkSessionResponse(sessionId, aSessionToken, aSessionSecret, aDeviceLinkBase);
  }

  public static String initialCallbackUrl() {
    return demoProperties.callbackUrl() + "?value=" + aCallbackToken;
  }

  public static SmartIdSession aDeviceLinkSession(Instant createdAt) {
    var request =
        new DeviceLinkAuthenticationSessionRequest(
            demoRelyingPartyUuid,
            demoProperties.relyingPartyName(),
            "QUALIFIED",
            SignatureProtocol.ACSP_V2,
            new AcspV2SignatureProtocolParameters(
                aRpChallenge, "rsassa-pss", new SignatureAlgorithmParameters("SHA3-512")),
            InteractionUtil.encodeToBase64(
                InteractionsMapper.from(
                    List.of(DeviceLinkInteraction.displayTextAndPin(loginPrompt)))),
            null,
            null,
            initialCallbackUrl());
    return new SmartIdSession(
        createdAt,
        new DeviceLinkLogin(
            aSessionId,
            aSessionToken,
            aSessionSecret,
            aDeviceLinkBase,
            request,
            aCallbackToken,
            initialCallbackUrl(),
            "est"));
  }

  public static SmartIdSession aNotificationSession(Instant createdAt) {
    var request =
        new NotificationAuthenticationSessionRequest(
            demoRelyingPartyUuid,
            demoProperties.relyingPartyName(),
            "QUALIFIED",
            SignatureProtocol.ACSP_V2.name(),
            new AcspV2SignatureProtocolParameters(
                aRpChallenge, "rsassa-pss", new SignatureAlgorithmParameters("SHA3-512")),
            InteractionUtil.encodeToBase64(
                InteractionsMapper.from(
                    List.of(NotificationInteraction.displayTextAndPin(loginPrompt)))),
            null,
            null,
            "numeric4");
    return new SmartIdSession(createdAt, new NotificationLogin(aSessionId, request, "1234"));
  }

  public static SessionStatus runningStatus() {
    var status = new SessionStatus();
    status.setState("RUNNING");
    return status;
  }

  public static SessionStatus completeStatus(String flowType) {
    var result = new SessionResult();
    result.setEndResult("OK");
    result.setDocumentNumber(documentNumber);

    var maskGenParameters = new SessionMaskGenAlgorithmParameters();
    maskGenParameters.setHashAlgorithm("SHA3-512");
    var maskGen = new SessionMaskGenAlgorithm();
    maskGen.setAlgorithm("id-mgf1");
    maskGen.setParameters(maskGenParameters);
    var algorithmParameters = new SessionSignatureAlgorithmParameters();
    algorithmParameters.setHashAlgorithm("SHA3-512");
    algorithmParameters.setMaskGenAlgorithm(maskGen);
    algorithmParameters.setSaltLength(64);
    algorithmParameters.setTrailerField("0xbc");

    var signature = new SessionSignature();
    signature.setValue(Base64.getEncoder().encodeToString("signature".getBytes(UTF_8)));
    signature.setServerRandom(
        Base64.getEncoder().encodeToString("server-random-server-random".getBytes(UTF_8)));
    signature.setUserChallenge("XtPfaGa8JnGtYrJjboooUf0KfY9sMEHrWFpSQrsUv9c");
    signature.setFlowType(flowType);
    signature.setSignatureAlgorithm("rsassa-pss");
    signature.setSignatureAlgorithmParameters(algorithmParameters);

    var certificate = new SessionCertificate();
    certificate.setCertificateLevel("QUALIFIED");
    certificate.setValue(demoTestAccountSigningCertificate);

    var status = new SessionStatus();
    status.setState("COMPLETE");
    status.setResult(result);
    status.setSignatureProtocol("ACSP_V2");
    status.setSignature(signature);
    status.setCert(certificate);
    status.setInteractionTypeUsed("displayTextAndPIN");
    return status;
  }

  public static SessionStatus failedStatus(String endResult) {
    var result = new SessionResult();
    result.setEndResult(endResult);
    var status = new SessionStatus();
    status.setState("COMPLETE");
    status.setResult(result);
    return status;
  }

  public static String sessionSecretDigest(String sessionSecret) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(sessionSecret));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static SmartIdCallback aCallback() {
    return new SmartIdCallback(
        aCallbackToken, sessionSecretDigest(aSessionSecret), "user-challenge-verifier");
  }
}
