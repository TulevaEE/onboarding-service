package ee.tuleva.onboarding.analytics.secondpillar;

import static ee.tuleva.onboarding.notification.email.EmailType.SECOND_PILLAR_ABANDONMENT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class SecondPillarAbandonmentRepositoryTest {

  private final SecondPillarAbandonmentRepository repository =
      new SecondPillarAbandonmentRepository((JdbcClient) null);

  @Test
  void getEmailTypeIsSecondPillarAbandonment() {
    assertThat(repository.getEmailType()).isEqualTo(SECOND_PILLAR_ABANDONMENT);
  }
}
