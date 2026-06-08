package ee.tuleva.onboarding.zzz;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UserPollutionProbeTest {

  @Autowired private UserRepository userRepository;

  @Test
  void noLeakedFixtureUserSurvivesTheSuite() {
    assertThat(userRepository.findByPersonalCode("38812121215"))
        .as(
            "A non-transactional spec committed the shared fixture user (personalCode=38812121215)"
                + " and poisoned the shared H2 DB for every later spec that inserts that user")
        .isEmpty();
  }
}
