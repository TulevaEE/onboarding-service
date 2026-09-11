package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.mandate.LegacyMandateSubmission;
import ee.tuleva.onboarding.mandate.MandateGateway;
import ee.tuleva.onboarding.mandate.MandateProcessResult;
import ee.tuleva.onboarding.mandate.MandateSubmissionCommand;
import ee.tuleva.onboarding.mandate.application.ApplicationSnapshot;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EpisMandateGateway implements MandateGateway {

  private final EpisService episService;

  @Override
  public List<ApplicationSnapshot> getApplications(Person person) {
    return episService.getApplications(person);
  }

  @Override
  public MandateProcessResult sendMandateV2(MandateSubmissionCommand<?> mandate) {
    return episService.sendMandateV2(mandate);
  }

  @Override
  public MandateProcessResult sendMandate(LegacyMandateSubmission mandate) {
    return episService.sendMandate(mandate);
  }
}
