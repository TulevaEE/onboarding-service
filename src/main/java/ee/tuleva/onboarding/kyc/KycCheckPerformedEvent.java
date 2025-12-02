package ee.tuleva.onboarding.kyc;

import java.util.Objects;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class KycCheckPerformedEvent extends ApplicationEvent {

  private final String personalCode;
  private final KycCheck kycCheck;

  public KycCheckPerformedEvent(Object source, String personalCode, KycCheck kycCheck) {
    super(source);
    this.personalCode = Objects.requireNonNull(personalCode);
    this.kycCheck = Objects.requireNonNull(kycCheck);
  }
}
