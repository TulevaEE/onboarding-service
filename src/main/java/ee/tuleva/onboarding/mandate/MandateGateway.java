package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.mandate.application.ApplicationSnapshot;
import java.util.List;

public interface MandateGateway {

  List<ApplicationSnapshot> getApplications(Person person);

  MandateProcessResult sendMandateV2(MandateSubmissionCommand<?> mandate);

  MandateProcessResult sendMandate(LegacyMandateSubmission mandate);
}
