package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static ee.tuleva.onboarding.user.ContactField.PHONE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContactChangeTest {

  private static final Instant CHANGED_AT = Instant.parse("2026-11-18T10:00:00Z");

  @Test
  void anEmailEditedOnlyInLetterCaseOnlyChangesLetterCase() {
    assertThat(
            new ContactChange(EMAIL, "mari@example.com", "Mari@Example.com", CHANGED_AT)
                .onlyChangesLetterCase())
        .isTrue();
  }

  @Test
  void anEmailEditedIntoALookAlikeOfAnotherMailboxChangesMoreThanLetterCase() {
    assertThat(
            new ContactChange(EMAIL, "key@example.com", "\u212Aey@example.com", CHANGED_AT)
                .onlyChangesLetterCase())
        .isFalse();
    assertThat(
            new ContactChange(EMAIL, "pin@example.com", "p\u0131n@example.com", CHANGED_AT)
                .onlyChangesLetterCase())
        .isFalse();
    assertThat(
            new ContactChange(EMAIL, "send@example.com", "\u017Fend@example.com", CHANGED_AT)
                .onlyChangesLetterCase())
        .isFalse();
  }

  @Test
  void aNewEmailAddressChangesMoreThanLetterCase() {
    assertThat(
            new ContactChange(EMAIL, "old@example.com", "new@example.com", CHANGED_AT)
                .onlyChangesLetterCase())
        .isFalse();
  }

  @Test
  void aFirstOrARemovedValueChangesMoreThanLetterCase() {
    assertThat(
            new ContactChange(EMAIL, null, "new@example.com", CHANGED_AT).onlyChangesLetterCase())
        .isFalse();
    assertThat(new ContactChange(PHONE, "+37255555555", null, CHANGED_AT).onlyChangesLetterCase())
        .isFalse();
  }

  @Test
  void keepsTheContactValuesOutOfItsStringForm() {
    var change =
        new ContactChange(
            EMAIL, "old@example.com", "new@example.com", Instant.parse("2026-11-18T10:00:00Z"));

    assertThat(change.toString())
        .isEqualTo("ContactChange[field=EMAIL, changedAt=2026-11-18T10:00:00Z]");
  }
}
