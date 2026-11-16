package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.kyb.RegistryCode;
import lombok.Getter;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEvent;

@Getter
@NullMarked
public class LegalEntityOnboardedEvent extends ApplicationEvent {

  private final OnboardedCompany company;

  public LegalEntityOnboardedEvent(Object source, RegistryCode registryCode, String name) {
    super(source);
    this.company = new OnboardedCompany(registryCode, name);
  }

  public record OnboardedCompany(RegistryCode registryCode, String name) {}
}
