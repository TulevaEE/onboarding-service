package ee.tuleva.onboarding.notification.email;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.getNameMergeVars;
import static ee.tuleva.onboarding.notification.email.EmailType.CONTACT_DETAILS_CHANGED;
import static ee.tuleva.onboarding.notification.email.EmailType.PREVIOUS_CONTACT_ACTIVITY;
import static ee.tuleva.onboarding.user.ContactChangeSource.SELF_SERVICE;
import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.microtripit.mandrillapp.lutung.model.LutungGsonUtils;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.user.ContactChange;
import ee.tuleva.onboarding.user.ContactDetailsChanged;
import ee.tuleva.onboarding.user.RecentlyReplacedEmails;
import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

@ExtendWith(MockitoExtension.class)
class PreviousContactNoticesTest {

  private static final List<String> TAGS = List.of("previous_contact");
  private static final Instant SIGNED_AT = Instant.parse("2026-11-21T09:00:00Z");
  private static final ContactChange EMAIL_CHANGE =
      new ContactChange(
          EMAIL, "old@example.com", "new@example.com", Instant.parse("2026-11-20T10:00:00Z"));

  @Mock RecentlyReplacedEmails recentlyReplacedEmails;
  @Mock EmailService emailService;
  @Mock EmailPersistenceService emailPersistenceService;

  PreviousContactNotices notices;

  @BeforeEach
  void setUp() {
    notices =
        new PreviousContactNotices(
            recentlyReplacedEmails, emailService, emailPersistenceService, new LocaleService());
  }

  @AfterEach
  void resetLocale() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void
      aCommittedEmailChangeIsSentToTheReplacedAddressInTheRequestLanguageAndRecordedWithoutABatch() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    var user = sampleUserNonMember().email("new@example.com").build();
    given(recentlyReplacedEmails.of(user.getIdOrThrow(), EMAIL_CHANGE.changedAt()))
        .willReturn(List.of("old@example.com"));
    var message = new MandrillMessage();
    given(
            emailService.newMandrillMessage(
                "old@example.com",
                "contact_details_changed_en",
                nameMergeVarsAnd(
                    user,
                    Map.of("emailChanged", true, "phoneChanged", false, "previousAddress", true)),
                TAGS))
        .willReturn(message);
    given(emailService.send(user, message, "contact_details_changed_en"))
        .willReturn(Optional.of(accepted("old-message")));

    notices.onContactDetailsChanged(
        new ContactDetailsChanged(user, SELF_SERVICE, List.of(EMAIL_CHANGE)));

    then(emailPersistenceService)
        .should()
        .save(user, "old-message", CONTACT_DETAILS_CHANGED, "sent");
    then(emailPersistenceService).shouldHaveNoMoreInteractions();
  }

  @Test
  void aFailingLookupOfReplacedEmailsDoesNotFailTheCommittedChange() {
    var user = sampleUserNonMember().email("new@example.com").build();
    willThrow(new IllegalStateException())
        .given(recentlyReplacedEmails)
        .of(user.getIdOrThrow(), EMAIL_CHANGE.changedAt());

    assertThatCode(
            () ->
                notices.onContactDetailsChanged(
                    new ContactDetailsChanged(user, SELF_SERVICE, List.of(EMAIL_CHANGE))))
        .doesNotThrowAnyException();
    then(emailService).shouldHaveNoInteractions();
  }

  @Test
  void aSignedWithdrawalIsSentToEveryReplacedAddressInItsLanguageWithOnlyTheNameAndNoBatch() {
    var user = sampleUserNonMember().email("new@example.com").build();
    given(recentlyReplacedEmails.of(user.getIdOrThrow(), SIGNED_AT))
        .willReturn(List.of("old@example.com", "older@example.com"));
    var toOld = new MandrillMessage();
    var toOlder = new MandrillMessage();
    given(
            emailService.newMandrillMessage(
                "old@example.com", "previous_contact_activity_et", getNameMergeVars(user), TAGS))
        .willReturn(toOld);
    given(
            emailService.newMandrillMessage(
                "older@example.com", "previous_contact_activity_et", getNameMergeVars(user), TAGS))
        .willReturn(toOlder);
    given(emailService.send(user, toOld, "previous_contact_activity_et"))
        .willReturn(Optional.of(accepted("old-message")));
    given(emailService.send(user, toOlder, "previous_contact_activity_et"))
        .willReturn(Optional.of(accepted("older-message")));

    notices.sendWithdrawalNotice(user, Locale.of("et"), SIGNED_AT);

    then(emailPersistenceService)
        .should()
        .save(user, "old-message", PREVIOUS_CONTACT_ACTIVITY, "sent");
    then(emailPersistenceService)
        .should()
        .save(user, "older-message", PREVIOUS_CONTACT_ACTIVITY, "sent");
    then(emailPersistenceService).shouldHaveNoMoreInteractions();
  }

  @Test
  void aRecipientWhoseNoticeCannotBeRecordedDoesNotKeepTheNextOneFromBeingNoticed() {
    var user = sampleUserNonMember().email("new@example.com").build();
    given(recentlyReplacedEmails.of(user.getIdOrThrow(), SIGNED_AT))
        .willReturn(List.of("old@example.com", "older@example.com"));
    var toOld = new MandrillMessage();
    var toOlder = new MandrillMessage();
    given(
            emailService.newMandrillMessage(
                "old@example.com", "previous_contact_activity_en", getNameMergeVars(user), TAGS))
        .willReturn(toOld);
    given(
            emailService.newMandrillMessage(
                "older@example.com", "previous_contact_activity_en", getNameMergeVars(user), TAGS))
        .willReturn(toOlder);
    given(emailService.send(user, toOld, "previous_contact_activity_en"))
        .willReturn(Optional.of(accepted("old-message")));
    given(emailService.send(user, toOlder, "previous_contact_activity_en"))
        .willReturn(Optional.of(accepted("older-message")));
    willThrow(new IllegalStateException())
        .given(emailPersistenceService)
        .save(user, "old-message", PREVIOUS_CONTACT_ACTIVITY, "sent");

    assertThatCode(() -> notices.sendWithdrawalNotice(user, Locale.ENGLISH, SIGNED_AT))
        .doesNotThrowAnyException();

    then(emailPersistenceService)
        .should()
        .save(user, "older-message", PREVIOUS_CONTACT_ACTIVITY, "sent");
  }

  @Test
  void aNoticeMandrillDidNotAcceptIsNotRecorded() {
    var user = sampleUserNonMember().email("new@example.com").build();
    given(recentlyReplacedEmails.of(user.getIdOrThrow(), SIGNED_AT))
        .willReturn(List.of("old@example.com"));
    var message = new MandrillMessage();
    given(
            emailService.newMandrillMessage(
                "old@example.com", "previous_contact_activity_en", getNameMergeVars(user), TAGS))
        .willReturn(message);
    given(emailService.send(user, message, "previous_contact_activity_en"))
        .willReturn(Optional.empty());

    notices.sendWithdrawalNotice(user, Locale.ENGLISH, SIGNED_AT);

    then(emailPersistenceService).shouldHaveNoInteractions();
  }

  @Test
  void aSignedWithdrawalWithNoReplacedAddressSendsNothing() {
    var user = sampleUserNonMember().email("new@example.com").build();
    given(recentlyReplacedEmails.of(user.getIdOrThrow(), SIGNED_AT)).willReturn(List.of());

    notices.sendWithdrawalNotice(user, Locale.ENGLISH, SIGNED_AT);

    then(emailService).shouldHaveNoInteractions();
    then(emailPersistenceService).shouldHaveNoInteractions();
  }

  private static Map<String, Object> nameMergeVarsAnd(User user, Map<String, Object> flags) {
    var mergeVars = new HashMap<>(getNameMergeVars(user));
    mergeVars.putAll(flags);
    return mergeVars;
  }

  private static MandrillMessageStatus accepted(String id) {
    return LutungGsonUtils.getGson()
        .fromJson("{\"_id\": \"" + id + "\", \"status\": \"sent\"}", MandrillMessageStatus.class);
  }
}
