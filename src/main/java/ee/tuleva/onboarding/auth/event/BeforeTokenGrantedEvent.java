package ee.tuleva.onboarding.auth.event;

import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import org.jspecify.annotations.NullMarked;

/**
 * @deprecated This class is deprecated and will be removed in the future. Use {@link
 *     AfterTokenGrantedEvent} instead.
 */
@Deprecated
@NullMarked
public class BeforeTokenGrantedEvent extends TokenGrantedEvent {

  public BeforeTokenGrantedEvent(Object source, AuthenticatedPerson person, GrantType grantType) {
    super(source, person, grantType);
  }
}
