package ee.tuleva.onboarding.mandate.event;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import java.util.Locale;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public abstract class AfterMandateSignedEvent extends ApplicationEvent {

  private final User user;
  private final Mandate mandate;
  private final Locale locale;

  public AfterMandateSignedEvent(Object source, User user, Mandate mandate, Locale locale) {
    super(source);
    this.user = user;
    this.mandate = mandate;
    this.locale = locale;
  }

  public static AfterMandateSignedEvent newInstance(
      Object source, User user, Mandate mandate, Locale locale) {
    if (mandate.getPillar() == 2) {
      return new SecondPillarAfterMandateSignedEvent(source, user, mandate, locale);
    } else if (mandate.getPillar() == 3) {
      return new ThirdPillarAfterMandateSignedEvent(source, user, mandate, locale);
    } else {
      throw new IllegalArgumentException("Event for the pillar type is not available");
    }
  }

  public Address getAddress() {
    return mandate.getAddress();
  }
}
