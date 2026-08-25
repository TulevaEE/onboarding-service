package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.kyb.CompanyDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class LegalEntityOnboardedEvent extends ApplicationEvent {

  private final CompanyDto company;

  public LegalEntityOnboardedEvent(Object source, CompanyDto company) {
    super(source);
    this.company = company;
  }
}
