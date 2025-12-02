package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.UserRepository;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(SavingsFundOnboardingRepository.class)
class SavingsFundOnboardingRepositoryTest {

  @Autowired SavingsFundOnboardingRepository repository;
  @Autowired UserRepository userRepository;

  @Test
  void isOnboardingCompleted_returnsTrueOnlyWhenStatusIsCompleted() {
    var completedUserId = createUser("38009293505");
    var pendingUserId = createUser("39609307495");
    var rejectedUserId = createUser("39910273027");
    var noRecordUserId = createUser("37603135585");

    repository.saveOnboardingStatus(completedUserId, COMPLETED);
    repository.saveOnboardingStatus(pendingUserId, PENDING);
    repository.saveOnboardingStatus(rejectedUserId, REJECTED);

    assertThat(repository.isOnboardingCompleted(completedUserId)).isTrue();
    assertThat(repository.isOnboardingCompleted(pendingUserId)).isFalse();
    assertThat(repository.isOnboardingCompleted(rejectedUserId)).isFalse();
    assertThat(repository.isOnboardingCompleted(noRecordUserId)).isFalse();
    assertThat(repository.isOnboardingCompleted(new Random().nextLong())).isFalse();
  }

  @Test
  void saveOnboardingStatus_insertsNewRecord() {
    var userId = createUser("37605030299");

    repository.saveOnboardingStatus(userId, PENDING);

    assertThat(repository.findStatusByUserId(userId)).contains(PENDING);
  }

  @Test
  void saveOnboardingStatus_updatesExistingRecord() {
    var userId = createUser("39201070898");

    repository.saveOnboardingStatus(userId, PENDING);
    repository.saveOnboardingStatus(userId, COMPLETED);

    assertThat(repository.findStatusByUserId(userId)).contains(COMPLETED);
  }

  private Long createUser(String personalCode) {
    return userRepository
        .save(
            sampleUserNonMember()
                .id(null)
                .personalCode(personalCode)
                .email(personalCode + "@test.ee")
                .build())
        .getId();
  }
}
