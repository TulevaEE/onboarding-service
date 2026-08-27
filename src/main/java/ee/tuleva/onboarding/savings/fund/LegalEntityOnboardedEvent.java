package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.kyb.CompanyDto;
import lombok.Getter;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEvent;

@Getter
@NullMarked
public class LegalEntityOnboardedEvent extends ApplicationEvent {

  private final CompanyDto company;

  public LegalEntityOnboardedEvent(Object source, CompanyDto company) {
    super(source);
    this.company = company;
  }
}
