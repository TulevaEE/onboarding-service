package ee.tuleva.onboarding.auth.event;

import static ee.tuleva.onboarding.auth.GrantType.ID_CARD;

import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.idcard.IdCardSession;
import ee.tuleva.onboarding.auth.idcard.IdDocumentType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
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
    final var attributeValue = person.getAttribute(IdCardSession.ID_DOCUMENT_TYPE_ATTRIBUTE);
    if (attributeValue == null) {
      return null;
    }
    return IdDocumentType.valueOf(attributeValue);
  }
}
