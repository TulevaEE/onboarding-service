package ee.tuleva.onboarding.mandate.event;

import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OnMandateBatchFailedEvent extends ApplicationEvent {

  private final User user;
  private final MandateBatch mandateBatch;
  private final Locale locale;

  public OnMandateBatchFailedEvent(
      Object source, User user, MandateBatch mandateBatch, Locale locale) {
    super(source);
    this.user = user;
    this.mandateBatch = mandateBatch;
    this.locale = locale;
  }
}
