package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(SavingsFundOnboardingRepository.class)
class SavingsFundOnboardingRepositoryTest {

  @Autowired SavingsFundOnboardingRepository repository;

  @Test
  void isOnboardingCompleted_returnsTrueOnlyWhenStatusIsCompleted() {
    var completedPersonalCode = "38009293505";
    var pendingPersonalCode = "39609307495";
    var rejectedPersonalCode = "39910273027";
    var noRecordPersonalCode = "37603135585";

    repository.saveOnboardingStatus(completedPersonalCode, COMPLETED);
    repository.saveOnboardingStatus(pendingPersonalCode, PENDING);
    repository.saveOnboardingStatus(rejectedPersonalCode, REJECTED);

    assertThat(repository.isOnboardingCompleted(completedPersonalCode)).isTrue();
    assertThat(repository.isOnboardingCompleted(pendingPersonalCode)).isFalse();
    assertThat(repository.isOnboardingCompleted(rejectedPersonalCode)).isFalse();
    assertThat(repository.isOnboardingCompleted(noRecordPersonalCode)).isFalse();
    assertThat(repository.isOnboardingCompleted("99999999999")).isFalse();
  }

  @Test
  void saveOnboardingStatus_insertsNewRecord() {
    var personalCode = "37605030299";

    repository.saveOnboardingStatus(personalCode, PENDING);

    assertThat(repository.findStatusByPersonalCode(personalCode)).contains(PENDING);
  }

  @Test
  void saveOnboardingStatus_updatesExistingRecord() {
    var personalCode = "39201070898";

    repository.saveOnboardingStatus(personalCode, PENDING);
    repository.saveOnboardingStatus(personalCode, COMPLETED);

    assertThat(repository.findStatusByPersonalCode(personalCode)).contains(COMPLETED);
  }
}
