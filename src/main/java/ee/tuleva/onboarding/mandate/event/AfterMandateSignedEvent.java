package ee.tuleva.onboarding.mandate.event;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import java.util.Locale;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AfterMandateSignedEvent extends ApplicationEvent {

  private final User user;
  private final Mandate mandate;
  private final Locale locale;
  private final boolean partOfBatch;

  public AfterMandateSignedEvent(
      Object source, User user, Mandate mandate, Locale locale, boolean partOfBatch) {
    super(source);
    this.user = user;
    this.mandate = mandate;
    this.locale = locale;
    this.partOfBatch = partOfBatch;
  }

  public Integer getPillar() {
    return mandate.getPillar();
  }

  public Address getAddress() {
    return mandate.getAddress();
  }
}
