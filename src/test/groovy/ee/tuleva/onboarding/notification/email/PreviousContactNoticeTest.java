package ee.tuleva.onboarding.notification.email;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.notification.email.EmailType.CONTACT_DETAILS_CHANGED;
import static ee.tuleva.onboarding.notification.email.EmailType.PREVIOUS_CONTACT_ACTIVITY;
import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static ee.tuleva.onboarding.user.ContactField.PHONE;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.notification.email.PreviousContactNotice.Recipient;
import ee.tuleva.onboarding.user.ContactChange;
import ee.tuleva.onboarding.user.ContactField;
import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PreviousContactNoticeTest {

  private static final Map<String, Object> FIRST_NAME_ONLY = Map.of("fname", "Jordan");

  private static final Instant CHANGED_AT = Instant.parse("2026-11-20T10:00:00Z");

  @Test
  void anEmailChangeIsNoticedAtTheReplacedAddressAsAPreviousAddressAndNotAtTheNewOne() {
    var user = userWithEmail("new@example.com");

    var notice =
        PreviousContactNotice.ofContactChange(
            user,
            List.of(change(EMAIL, "old@example.com", "new@example.com")),
            List.of("old@example.com"));

    assertThat(notice)
        .isEqualTo(
            new PreviousContactNotice(
                CONTACT_DETAILS_CHANGED,
                List.of(
                    new Recipient(
                        "old@example.com",
                        firstNameAnd(
                            Map.of(
                                "emailChanged", true,
                                "phoneChanged", false,
                                "previousAddress", true))))));
  }

  @Test
  void anEmailChangeInsideAnEarlierWindowAlsoReachesTheEarlierAddresses() {
    var user = userWithEmail("third@example.com");

    var notice =
        PreviousContactNotice.ofContactChange(
            user,
            List.of(change(EMAIL, "second@example.com", "third@example.com")),
            List.of("second@example.com", "first@example.com"));

    assertThat(notice.recipients())
        .extracting(Recipient::email)
        .containsExactly("second@example.com", "first@example.com");
  }

  @Test
  void aPhoneChangeIsNoticedAtTheCurrentAddressSinceNoTextMessageIsSent() {
    var user = userWithEmail("current@example.com");

    var notice =
        PreviousContactNotice.ofContactChange(
            user, List.of(change(PHONE, "+37255555555", "+37251111111")), List.of());

    assertThat(notice)
        .isEqualTo(
            new PreviousContactNotice(
                CONTACT_DETAILS_CHANGED,
                List.of(
                    new Recipient(
                        "current@example.com",
                        firstNameAnd(
                            Map.of(
                                "emailChanged", false,
                                "phoneChanged", true,
                                "previousAddress", false))))));
  }

  @Test
  void
      aPhoneChangeSoonAfterAnEmailChangeTellsTheReplacedAddressWhyItHearsOfItButNotTheCurrentOne() {
    var user = userWithEmail("current@example.com");
    var phoneChanged = Map.<String, Object>of("emailChanged", false, "phoneChanged", true);

    var notice =
        PreviousContactNotice.ofContactChange(
            user,
            List.of(change(PHONE, "+37255555555", "+37251111111")),
            List.of("old@example.com"));

    assertThat(notice.recipients())
        .containsExactly(
            new Recipient(
                "old@example.com", firstNameAnd(with(phoneChanged, "previousAddress", true))),
            new Recipient(
                "current@example.com", firstNameAnd(with(phoneChanged, "previousAddress", false))));
  }

  @Test
  void aChangeOfBothIsNoticedAtTheReplacedAddressButNotAtTheNewOne() {
    var user = userWithEmail("new@example.com");

    var notice =
        PreviousContactNotice.ofContactChange(
            user,
            List.of(
                change(EMAIL, "old@example.com", "new@example.com"),
                change(PHONE, "+37255555555", "+37251111111")),
            List.of("old@example.com"));

    assertThat(notice)
        .isEqualTo(
            new PreviousContactNotice(
                CONTACT_DETAILS_CHANGED,
                List.of(
                    new Recipient(
                        "old@example.com",
                        firstNameAnd(
                            Map.of(
                                "emailChanged", true,
                                "phoneChanged", true,
                                "previousAddress", true))))));
  }

  @Test
  void aLetterCaseEditOfTheEmailWithANewPhoneNumberIsNoticedAtTheCurrentAddressAsAPhoneChange() {
    var user = userWithEmail("Mari@Example.com");

    var notice =
        PreviousContactNotice.ofContactChange(
            user,
            List.of(
                change(EMAIL, "mari@example.com", "Mari@Example.com"),
                change(PHONE, "+37255555555", "+37251111111")),
            List.of());

    assertThat(notice.recipients())
        .containsExactly(
            new Recipient(
                "Mari@Example.com",
                firstNameAnd(
                    Map.of(
                        "emailChanged", false,
                        "phoneChanged", true,
                        "previousAddress", false))));
  }

  @Test
  void aLetterCaseEditOfTheEmailAloneIsNoticedByNobodyNotEvenAnEarlierAddress() {
    var user = userWithEmail("Mari@Example.com");

    var notice =
        PreviousContactNotice.ofContactChange(
            user,
            List.of(change(EMAIL, "mari@example.com", "Mari@Example.com")),
            List.of("older@example.com"));

    assertThat(notice.recipients()).isEmpty();
  }

  @Test
  void aFirstEverEmailWithNoEarlierAddressIsNoticedByNobody() {
    var user = userWithEmail("first@example.com");

    var notice =
        PreviousContactNotice.ofContactChange(
            user, List.of(change(EMAIL, null, "first@example.com")), List.of());

    assertThat(notice.recipients()).isEmpty();
  }

  @Test
  void aPhoneChangeOfAPersonWithNoEmailIsNoticedByNobody() {
    var user = userWithEmail(null);

    var notice =
        PreviousContactNotice.ofContactChange(
            user, List.of(change(PHONE, null, "+37251111111")), List.of());

    assertThat(notice.recipients()).isEmpty();
  }

  @Test
  void aSignedWithdrawalIsNoticedAtEveryReplacedAddressWithNothingButTheFirstName() {
    var user = userWithEmail("new@example.com");

    var notice =
        PreviousContactNotice.ofWithdrawal(user, List.of("old@example.com", "older@example.com"));

    assertThat(notice)
        .isEqualTo(
            new PreviousContactNotice(
                PREVIOUS_CONTACT_ACTIVITY,
                List.of(
                    new Recipient("old@example.com", FIRST_NAME_ONLY),
                    new Recipient("older@example.com", FIRST_NAME_ONLY))));
  }

  @Test
  void aSignedWithdrawalWithNoReplacedAddressIsNoticedByNobody() {
    var notice = PreviousContactNotice.ofWithdrawal(userWithEmail("new@example.com"), List.of());

    assertThat(notice.recipients()).isEmpty();
  }

  private static User userWithEmail(@Nullable String email) {
    return sampleUserNonMember().email(email).build();
  }

  private static ContactChange change(
      ContactField field, @Nullable String oldValue, @Nullable String newValue) {
    return new ContactChange(field, oldValue, newValue, CHANGED_AT);
  }

  private static Map<String, Object> with(Map<String, Object> mergeVars, String key, Object value) {
    var extended = new HashMap<>(mergeVars);
    extended.put(key, value);
    return extended;
  }

  private static Map<String, Object> firstNameAnd(Map<String, Object> flags) {
    var mergeVars = new HashMap<>(FIRST_NAME_ONLY);
    mergeVars.putAll(flags);
    return mergeVars;
  }
}
