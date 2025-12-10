package ee.tuleva.onboarding.auth.webeid;

import static ee.tuleva.onboarding.auth.GrantType.GRANT_TYPE;
import static ee.tuleva.onboarding.auth.GrantType.ID_CARD;
import static ee.tuleva.onboarding.auth.idcard.IdCardSession.ID_DOCUMENT_TYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.auth.AuthProvider;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.idcard.IdCardSession;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import eu.webeid.security.authtoken.WebEidAuthToken;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class WebEidAuthProvider implements AuthProvider {

  private final WebEidAuthService webEidAuthService;
  private final GenericSessionStore sessionStore;
  private final PrincipalService principalService;
  private final ObjectMapper objectMapper;

  @Override
  public boolean supports(GrantType grantType) {
    return ID_CARD.equals(grantType);
  }

  @Override
  public AuthenticatedPerson authenticate(String authTokenJson) {
    if (authTokenJson == null) {
      return null;
    }

    WebEidAuthToken authToken = parseAuthToken(authTokenJson);
    IdCardSession idCardSession = webEidAuthService.authenticate(authToken);
    sessionStore.save(idCardSession);

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
