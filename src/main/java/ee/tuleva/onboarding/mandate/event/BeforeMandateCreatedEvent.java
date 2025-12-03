package ee.tuleva.onboarding.mandate.event;

import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class BeforeMandateCreatedEvent extends ApplicationEvent {

  private final User user;
  private final Mandate mandate;

  public BeforeMandateCreatedEvent(Object source, User user, Mandate mandate) {
    super(source);
    this.user = user;
    this.mandate = mandate;
  }

  public Integer getPillar() {
    return mandate.getPillar();
  }

  public Country getCountry() {
    return mandate.getCountry();
  }

  public boolean isThirdPillar() {
    return mandate.isThirdPillar();
  }
}
