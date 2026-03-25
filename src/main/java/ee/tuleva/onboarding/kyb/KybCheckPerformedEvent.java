package ee.tuleva.onboarding.kyb;

import java.util.List;
import java.util.Objects;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class KybCheckPerformedEvent extends ApplicationEvent {

  private final PersonalCode personalCode;
  private final List<KybCheck> checks;

  public KybCheckPerformedEvent(Object source, PersonalCode personalCode, List<KybCheck> checks) {
    super(source);
    this.personalCode = Objects.requireNonNull(personalCode);
    this.checks = Objects.requireNonNull(checks);
  }
}
