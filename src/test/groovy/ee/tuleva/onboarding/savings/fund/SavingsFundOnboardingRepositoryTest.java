package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
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
    repository.saveOnboardingStatus("38009293505", PERSON, COMPLETED);
    repository.saveOnboardingStatus("39609307495", PERSON, PENDING);
    repository.saveOnboardingStatus("39910273027", PERSON, REJECTED);

    assertThat(repository.isOnboardingCompleted("38009293505", PERSON)).isTrue();
    assertThat(repository.isOnboardingCompleted("39609307495", PERSON)).isFalse();
    assertThat(repository.isOnboardingCompleted("39910273027", PERSON)).isFalse();
    assertThat(repository.isOnboardingCompleted("37603135585", PERSON)).isFalse();
  }

  @Test
  void saveOnboardingStatus_insertsNewRecord() {
    repository.saveOnboardingStatus("37605030299", PERSON, PENDING);

    assertThat(repository.findStatus("37605030299", PERSON)).contains(PENDING);
  }

  @Test
  void saveOnboardingStatus_updatesExistingRecord() {
    repository.saveOnboardingStatus("39201070898", PERSON, PENDING);
    repository.saveOnboardingStatus("39201070898", PERSON, COMPLETED);

    assertThat(repository.findStatus("39201070898", PERSON)).contains(COMPLETED);
  }

  @Test
  void saveOnboardingStatus_separatesPersonAndLegalEntity() {
    repository.saveOnboardingStatus("14118923", LEGAL_ENTITY, COMPLETED);

    assertThat(repository.isOnboardingCompleted("14118923", LEGAL_ENTITY)).isTrue();
    assertThat(repository.isOnboardingCompleted("14118923", PERSON)).isFalse();
  }
}
