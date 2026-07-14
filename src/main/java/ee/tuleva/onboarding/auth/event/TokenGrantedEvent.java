package ee.tuleva.onboarding.auth.event;

import static ee.tuleva.onboarding.auth.GrantType.ID_CARD;
import static ee.tuleva.onboarding.auth.idcard.IdCardSession.ID_DOCUMENT_TYPE;

import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.idcard.IdDocumentType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import lombok.Getter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;

@Getter
@NullMarked
public abstract class TokenGrantedEvent extends ApplicationEvent {

  private final AuthenticatedPerson person;
  private final GrantType grantType;

  protected TokenGrantedEvent(Object source, AuthenticatedPerson person, GrantType grantType) {
    super(source);
    this.person = person;
    this.grantType = grantType;
  }

  public boolean isIdCard() {
    return ID_CARD.equals(grantType);
  }

  public @Nullable IdDocumentType getIdDocumentType() {
    final var attributeValue = person.getAttribute(ID_DOCUMENT_TYPE);
    if (attributeValue == null) {
      return null;
    }
    return IdDocumentType.valueOf(attributeValue);
  }
}
