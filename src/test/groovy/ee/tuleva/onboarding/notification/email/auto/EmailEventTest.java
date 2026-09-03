package ee.tuleva.onboarding.notification.email.auto;

import static ee.tuleva.onboarding.notification.email.EmailType.MEMBERSHIP;
import static ee.tuleva.onboarding.notification.email.EmailType.SECOND_PILLAR_LEAVERS;
import static ee.tuleva.onboarding.notification.email.auto.EmailEvent.NEW_LEAVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailEventTest {

  @Test
  void findsTheEventMatchingAnEmailType() {
    assertThat(EmailEvent.getByEmailType(SECOND_PILLAR_LEAVERS)).isEqualTo(NEW_LEAVER);
  }

  @Test
  void throwsWhenNoEventIsMappedToTheEmailType() {
    assertThatThrownBy(() -> EmailEvent.getByEmailType(MEMBERSHIP))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
