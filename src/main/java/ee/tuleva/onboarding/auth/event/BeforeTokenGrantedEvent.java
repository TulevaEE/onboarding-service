package ee.tuleva.onboarding.auth.event;

import static ee.tuleva.onboarding.auth.GrantType.ID_CARD;
import static ee.tuleva.onboarding.auth.idcard.IdCardSession.ID_DOCUMENT_TYPE;

import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.idcard.IdDocumentType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @deprecated This class is deprecated and will be removed in the future. Use {@link
 *     AfterTokenGrantedEvent} instead.
 */
@Getter
@Deprecated
@RequiredArgsConstructor
public class BeforeTokenGrantedEvent {

  private final AuthenticatedPerson person;
  private final GrantType grantType;

  public boolean isIdCard() {
    return ID_CARD.equals(grantType);
  }

  public IdDocumentType getIdDocumentType() {
    final var attributeValue = person.getAttribute(ID_DOCUMENT_TYPE);
    if (attributeValue == null) {
      return null;
    }
    return IdDocumentType.valueOf(attributeValue);
  }
}
