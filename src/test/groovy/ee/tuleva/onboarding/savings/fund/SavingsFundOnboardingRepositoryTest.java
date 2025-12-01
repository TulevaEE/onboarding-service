package ee.tuleva.onboarding.savings.fund;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.User;
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
  void isOnboardingCompleted_returnsFalseWhenNotOnboarded() {
    var userId = createUser("33306182790");

    assertThat(repository.isOnboardingCompleted(userId)).isFalse();
    assertThat(repository.isOnboardingCompleted(new Random().nextLong())).isFalse();
  }

  @Test
  void completeOnboarding_marksUserAsOnboarded() {
    var userId = createUser("46102110140");

    repository.completeOnboarding(userId);

    assertThat(repository.isOnboardingCompleted(userId)).isTrue();
  }

  private Long createUser(String personalCode) {
    return userRepository
        .save(User.builder().firstName("John").lastName("Smith").personalCode(personalCode).build())
        .getId();
  }
}
