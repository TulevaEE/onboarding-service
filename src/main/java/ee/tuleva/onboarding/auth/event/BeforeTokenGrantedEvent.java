package ee.tuleva.onboarding.auth.event;

import static ee.tuleva.onboarding.auth.GrantType.ID_CARD;
import static ee.tuleva.onboarding.auth.idcard.IdCardSession.ID_DOCUMENT_TYPE;

import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.idcard.IdDocumentType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
@Deprecated
/**
 * @deprecated This class is deprecated and will be removed in the future.
 *     Use {@link AfterTokenGrantedEvent} instead.
 */
public class BeforeTokenGrantedEvent extends ApplicationEvent {

  private final AuthenticatedPerson person;
  private final GrantType grantType;

  public BeforeTokenGrantedEvent(Object source, AuthenticatedPerson person, GrantType grantType) {
    super(source);
    this.person = person;
    this.grantType = grantType;
  }

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
