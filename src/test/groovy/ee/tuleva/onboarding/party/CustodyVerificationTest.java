package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.NO_CUSTODY;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.OK;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustodyVerificationTest {

  private static final String CHILD = "61506150006";
  private static final Map<String, Object> EVIDENCE = Map.of("outcome", "OK");

  @Test
  void evidenceWithCitizenships_recordsAnEmptyListWhenTheRegisterKnowsNoCitizenship() {
    var verification = new CustodyVerification(OK, child(null, List.of()), EVIDENCE);

    assertThat(verification.evidenceWithCitizenships())
        .isEqualTo(Map.of("outcome", "OK", "citizenships", List.of()));
  }

  @Test
  void evidenceWithCitizenships_recordsTheScalarCitizenshipAlongsideEveryCitizenship() {
    var verification = new CustodyVerification(OK, child("EE", List.of("EE", "RU")), EVIDENCE);

    assertThat(verification.evidenceWithCitizenships())
        .isEqualTo(
            Map.of("outcome", "OK", "citizenship", "EE", "citizenships", List.of("EE", "RU")));
  }

  @Test
  void evidenceWithCitizenships_leavesEvidenceUntouchedWhenThereIsNoChild() {
    var verification = CustodyVerification.notVerified(NO_CUSTODY, EVIDENCE);

    assertThat(verification.evidenceWithCitizenships()).isEqualTo(EVIDENCE);
  }

  private PopulationRegisterPerson child(String citizenship, List<String> citizenships) {
    return new PopulationRegisterPerson(
        CHILD, "Mari", "Maasikas", LocalDate.of(2015, 6, 15), ALIVE, citizenship, citizenships);
  }
}
