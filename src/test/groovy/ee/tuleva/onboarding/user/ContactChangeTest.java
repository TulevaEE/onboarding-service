package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContactChangeTest {

  @Test
  void keepsTheContactValuesOutOfItsStringForm() {
    var change =
        new ContactChange(
            EMAIL, "old@example.com", "new@example.com", Instant.parse("2026-11-18T10:00:00Z"));

    assertThat(change.toString())
        .isEqualTo("ContactChange[field=EMAIL, changedAt=2026-11-18T10:00:00Z]");
  }
}
