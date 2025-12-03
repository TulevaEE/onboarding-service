package ee.tuleva.onboarding.mandate.event;

import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AfterMandateSignedEvent extends ApplicationEvent {

  private final User user;
  private final Mandate mandate;
  private final Locale locale;

  public AfterMandateSignedEvent(Object source, User user, Mandate mandate, Locale locale) {
    super(source);
    this.user = user;
    this.mandate = mandate;
    this.locale = locale;
  }

  public Integer getPillar() {
    return mandate.getPillar();
  }

  public Country getAddress() {
    return mandate.getAddress();
  }
}
