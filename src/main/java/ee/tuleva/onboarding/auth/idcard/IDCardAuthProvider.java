package ee.tuleva.onboarding.auth.idcard;

import static ee.tuleva.onboarding.auth.idcard.IdCardSession.ID_DOCUMENT_TYPE_ATTRIBUTE;

import ee.tuleva.onboarding.auth.AuthProvider;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.idcard.exception.IdCardSessionNotFoundException;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IDCardAuthProvider implements AuthProvider {
  private final GenericSessionStore sessionStore;
  private final PrincipalService principalService;

  @Override
  public boolean supports(GrantType grantType) {
    return GrantType.ID_CARD.equals(grantType);
  }

  @Override
  public AuthenticatedPerson authenticate(String authenticationHash) {
    Optional<IdCardSession> session = sessionStore.get(IdCardSession.class);
    if (session.isEmpty()) {
      throw new IdCardSessionNotFoundException();
    }
    IdCardSession idCardSession = session.get();
    return principalService.getFrom(
        idCardSession, Map.of(ID_DOCUMENT_TYPE_ATTRIBUTE, idCardSession.documentType.name()));
  }
}
