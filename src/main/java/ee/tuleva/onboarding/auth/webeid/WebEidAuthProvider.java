package ee.tuleva.onboarding.auth.webeid;

import static ee.tuleva.onboarding.auth.GrantType.GRANT_TYPE;
import static ee.tuleva.onboarding.auth.GrantType.ID_CARD;
import static ee.tuleva.onboarding.auth.idcard.IdCardSession.ID_DOCUMENT_TYPE;

import ee.tuleva.onboarding.auth.AuthProvider;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.idcard.IdCardSession;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import eu.webeid.security.authtoken.WebEidAuthToken;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebEidAuthProvider implements AuthProvider {

  private final WebEidAuthService webEidAuthService;
  private final PrincipalService principalService;
  private final JsonMapper objectMapper;

  @Override
  public boolean supports(GrantType grantType) {
    return ID_CARD.equals(grantType);
  }

  @Override
  public AuthenticatedPerson authenticate(@Nullable String authTokenJson) {
    if (authTokenJson == null) {
      throw new WebEidAuthException("Missing Web eID authentication token");
    }

    WebEidAuthToken authToken = parseAuthToken(authTokenJson);
    IdCardSession idCardSession = webEidAuthService.authenticate(authToken);

    return principalService.getFrom(
        idCardSession,
        Map.of(
            ID_DOCUMENT_TYPE, idCardSession.getDocumentType().name(), GRANT_TYPE, ID_CARD.name()));
  }

  private WebEidAuthToken parseAuthToken(String authTokenJson) {
    try {
      return objectMapper.readValue(authTokenJson, WebEidAuthToken.class);
    } catch (Exception e) {
      log.error("Failed to parse auth token JSON", e);
      throw new WebEidAuthException("Invalid auth token format", e);
    }
  }
}
