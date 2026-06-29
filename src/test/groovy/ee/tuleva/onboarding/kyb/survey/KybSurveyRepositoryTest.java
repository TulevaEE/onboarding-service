package ee.tuleva.onboarding.kyb.survey;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.InvestmentGoal;
import ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.InvestmentGoals;
import ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.InvestmentGoalsValue;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class KybSurveyRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private KybSurveyRepository repository;

  @Test
  void roundTripsLegacyOptionAndNewTextInvestmentGoalsWithoutMigration() {
    var user = createUser("37605030299");

    var survey =
        new KybSurveyResponse(
            List.of(
                new InvestmentGoals(new InvestmentGoalsValue.Option(InvestmentGoal.LONG_TERM)),
                new InvestmentGoals(
                    new InvestmentGoalsValue.Text("Soovin investeerida kinnisvarasse"))));

    var saved =
        repository.save(
            KybSurvey.builder()
                .userId(user.getId())
                .registryCode("12345678")
                .survey(survey)
                .build());
    entityManager.flush();
    entityManager.clear();

    var reloaded = repository.findById(saved.getId()).orElseThrow();

    assertThat(reloaded.getSurvey()).isEqualTo(survey);
  }

  private User createUser(String personalCode) {
    var user = new User();
    user.setPersonalCode(personalCode);
    user.setFirstName("Jaan");
    user.setLastName("Tamm");
    user.setEmail("jaan.tamm@example.com");
    return entityManager.persist(user);
  }
}
