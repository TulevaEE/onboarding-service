package ee.tuleva.onboarding.savings.fund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SavingsFundOnboardingRepositoryTest {

  @Autowired SavingsFundOnboardingRepository repository;
  @Autowired UserRepository userRepository;
  @Autowired NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  void isOnboardingCompleted() {
    var user1 = createUser("33306182790");
    var user2 = createUser("46102110140");

    jdbcTemplate.update(
        "insert into savings_fund_onboarding (user_id) values (:user_id)",
        Map.of("user_id", user1));

    assertThat(repository.isOnboardingCompleted(user1)).isTrue();
    assertThat(repository.isOnboardingCompleted(user2)).isFalse();
    assertThat(repository.isOnboardingCompleted(new Random().nextLong())).isFalse();
  }

  private Long createUser(String personalCode) {
    return userRepository
        .save(User.builder().firstName("John").lastName("Smith").personalCode(personalCode).build())
        .getId();
  }
}
