package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.user.ContactChangeSource.SELF_SERVICE;
import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContactDetailsChangedTest {

  private static final ContactChange EMAIL_CHANGE =
      new ContactChange(
          EMAIL, "old@example.com", "new@example.com", Instant.parse("2026-11-18T10:00:00Z"));

  @Test
  void rejectsAnEventWithoutChanges() {
    var user = sampleUserNonMember().build();

    assertThatThrownBy(() -> new ContactDetailsChanged(user, SELF_SERVICE, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void keepsTheChangesItWasCreatedWithWhenTheGivenListChangesLater() {
    var changes = new ArrayList<>(List.of(EMAIL_CHANGE));
    var event = new ContactDetailsChanged(sampleUserNonMember().build(), SELF_SERVICE, changes);

    changes.clear();

    assertThat(event.changes()).containsExactly(EMAIL_CHANGE);
  }
}
