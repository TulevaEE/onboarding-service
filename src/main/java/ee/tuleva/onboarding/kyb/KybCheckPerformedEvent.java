package ee.tuleva.onboarding.kyb;

import java.util.List;
import java.util.Objects;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class KybCheckPerformedEvent extends ApplicationEvent {

  private final CompanyDto company;
  // TODO: consider relying only on CompanyDTO ?
  private final PersonalCode personalCode;
  private final List<KybRelatedPerson> relatedPersons;
  private final List<KybCheck> checks;

  public KybCheckPerformedEvent(
      Object source,
      CompanyDto company,
      PersonalCode personalCode,
      List<KybRelatedPerson> relatedPersons,
      List<KybCheck> checks) {
    super(source);
    this.company = Objects.requireNonNull(company);
    this.personalCode = Objects.requireNonNull(personalCode);
    this.relatedPersons = Objects.requireNonNull(relatedPersons);
    this.checks = Objects.requireNonNull(checks);
  }
}
