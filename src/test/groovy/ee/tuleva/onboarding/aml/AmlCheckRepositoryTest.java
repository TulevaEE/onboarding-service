package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class AmlCheckRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private AmlCheckRepository repository;

  @Test
  void readsBackCompanyRiskLevelOverrideWithoutBreakingEnumDeserialization() {
    User user = sampleUser().build();
    AmlCheck override =
        AmlCheck.builder()
            .personalCode(user.getPersonalCode())
            .type(AmlCheckType.COMPANY_RISK_LEVEL_OVERRIDE)
            .success(true)
            .metadata(Map.of("level", 3, "registry_code", "12345678", "source", "manual"))
            .build();
    entityManager.persist(override);
    entityManager.flush();

    List<AmlCheck> checks =
        repository.findAllByPersonalCodeAndCreatedTimeAfter(
            user.getPersonalCode(), Instant.now().minus(365, DAYS));

    assertThat(checks)
        .extracting(AmlCheck::getType)
        .containsExactly(AmlCheckType.COMPANY_RISK_LEVEL_OVERRIDE);
  }
}
