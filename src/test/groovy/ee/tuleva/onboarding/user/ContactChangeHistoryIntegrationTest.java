package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.notification.email.EmailType.CONTACT_DETAILS_CHANGED;
import static ee.tuleva.onboarding.user.ContactChangeSource.SELF_SERVICE;
import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static ee.tuleva.onboarding.user.ContactField.PHONE;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import com.microtripit.mandrillapp.lutung.model.LutungGsonUtils;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.auth.ClientConnection;
import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.event.TrackableEventLogger;
import ee.tuleva.onboarding.event.broadcasting.ContactDetailsChangedBroadcaster;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.PreviousContactNotices;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.contact.ContactChangeRecorder;
import ee.tuleva.onboarding.user.contact.ContactChangeRepository;
import ee.tuleva.onboarding.user.contact.ContactChangeRows;
import ee.tuleva.onboarding.user.contact.ContactChangeRows.Row;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.member.MemberRepository;
import ee.tuleva.onboarding.user.name.NameChangeRecorder;
import ee.tuleva.onboarding.user.name.NameChangeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional(propagation = NOT_SUPPORTED)
@RecordApplicationEvents
@Import({
  UserService.class,
  ContactChangeRecorder.class,
  ContactChangeRepository.class,
  NameChangeRecorder.class,
  NameChangeRepository.class,
  ContactDetailsChangedBroadcaster.class,
  ClientConnection.class,
  TrackableEventLogger.class,
  ClockConfig.class,
  PreviousContactNotices.class,
  RecentlyReplacedEmails.class,
  ContactChangeConfiguration.class,
  EmailPersistenceService.class,
  LocaleService.class
})
class ContactChangeHistoryIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
  private static final String PERSONAL_CODE = "39802077017";

  @Autowired UserService userService;
  @Autowired UserRepository userRepository;
  @Autowired JdbcClient jdbcClient;
  @Autowired ApplicationEvents applicationEvents;
  @Autowired EventLogRepository eventLogRepository;
  @Autowired MemberRepository memberRepository;
  @Autowired EmailPersistenceService emailPersistenceService;

  @MockitoBean EmailService emailService;

  ContactChangeRows contactChanges;
  User user;

  record StoredContactDetails(@Nullable String email, @Nullable String phoneNumber) {}

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(Clock.fixed(NOW, UTC));
    contactChanges = new ContactChangeRows(jdbcClient);
    user =
        userRepository.save(
            User.builder()
                .firstName("Test")
                .lastName("Person")
                .personalCode(PERSONAL_CODE)
                .email("old@example.com")
                .phoneNumber("+37255555555")
                .build());
  }

  @AfterEach
  void cleanUp() {
    ClockHolder.setDefaultClock();
    LocaleContextHolder.resetLocaleContext();
    jdbcClient
        .sql("DELETE FROM email WHERE personal_code = :personalCode")
        .param("personalCode", PERSONAL_CODE)
        .update();
    jdbcClient
        .sql("DELETE FROM event_log WHERE principal = :principal")
        .param("principal", PERSONAL_CODE)
        .update();
    jdbcClient
        .sql("DELETE FROM user_contact_change WHERE user_id = :userId")
        .param("userId", user.getId())
        .update();
    jdbcClient
        .sql("DELETE FROM member WHERE user_id = :userId")
        .param("userId", user.getId())
        .update();
    jdbcClient.sql("DELETE FROM users WHERE id = :userId").param("userId", user.getId()).update();
  }

  @Test
  void changingTheEmailCommitsTheNewEmailTogetherWithItsHistoryRow() {
    userService.changeContactDetails(PERSONAL_CODE, "new@example.com", "+37255555555");

    assertThat(storedContactDetails())
        .isEqualTo(new StoredContactDetails("new@example.com", "+37255555555"));
    assertThat(contactChanges.of(user.getId()))
        .containsExactly(new Row(EMAIL, "old@example.com", "new@example.com", SELF_SERVICE, NOW));
  }

  @Test
  void changingTheEmailPublishesOneEventWithTheChangedUserAndTheChange() {
    userService.changeContactDetails(PERSONAL_CODE, "new@example.com", "+37255555555");

    var changedUser = userRepository.findById(user.getId()).orElseThrow();
    assertThat(applicationEvents.stream(ContactDetailsChanged.class))
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields(
            "user.createdDate", "user.updatedDate")
        .containsExactly(
            new ContactDetailsChanged(
                changedUser,
                SELF_SERVICE,
                List.of(new ContactChange(EMAIL, "old@example.com", "new@example.com", NOW))));
  }

  @Test
  void changingOnlyThePhoneNumberRecordsThePhoneNumberItReplaced() {
    userService.changeContactDetails(PERSONAL_CODE, "old@example.com", "+37251111111");

    assertThat(storedContactDetails())
        .isEqualTo(new StoredContactDetails("old@example.com", "+37251111111"));
    assertThat(contactChanges.of(user.getId()))
        .containsExactly(new Row(PHONE, "+37255555555", "+37251111111", SELF_SERVICE, NOW));
  }

  @Test
  void changingTheEmailLeavesAnAuditTrailNamingTheFieldButNotItsValues() {
    userService.changeContactDetails(PERSONAL_CODE, "new@example.com", "+37255555555");

    assertThat(eventLogsOfTheUser())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "timestamp")
        .containsExactly(
            EventLog.builder()
                .principal(PERSONAL_CODE)
                .type("CONTACT_DETAILS_CHANGE")
                .data(Map.of("fields", List.of("EMAIL"), "source", "SELF_SERVICE"))
                .build());
  }

  @Test
  void resubmittingTheSameContactDetailsStoresNothingAndPublishesNothing() {
    userService.changeContactDetails(PERSONAL_CODE, "old@example.com", "+37255555555");

    assertThat(contactChanges.of(user.getId())).isEmpty();
    assertThat(applicationEvents.stream(ContactDetailsChanged.class)).isEmpty();
    assertThat(eventLogsOfTheUser()).isEmpty();
  }

  @Test
  void changingTheEmailOfAMemberCommitsItWithItsHistoryRowAndKeepsTheMembership() {
    var member =
        memberRepository.save(Member.builder().user(user).memberNumber(234).active(true).build());

    var changedUser =
        userService.changeContactDetails(PERSONAL_CODE, "new@example.com", "+37255555555");

    assertThat(changedUser.getMember().map(Member::getId)).contains(member.getId());
    assertThat(storedContactDetails())
        .isEqualTo(new StoredContactDetails("new@example.com", "+37255555555"));
    assertThat(contactChanges.of(user.getId()))
        .containsExactly(new Row(EMAIL, "old@example.com", "new@example.com", SELF_SERVICE, NOW));
  }

  @Test
  void aChangeThatFailsWhenCommittedAfterItsHistoryAndAuditWereWrittenRollsThemBackWithTheUser() {
    assertThatThrownBy(
            () -> userService.changeContactDetails(PERSONAL_CODE, "not-an-email", "+37255555555"))
        .isInstanceOf(TransactionSystemException.class);

    assertThat(applicationEvents.stream(ContactDetailsChanged.class)).hasSize(1);
    assertThat(storedContactDetails())
        .isEqualTo(new StoredContactDetails("old@example.com", "+37255555555"));
    assertThat(contactChanges.of(user.getId())).isEmpty();
    assertThat(eventLogsOfTheUser()).isEmpty();
  }

  @Test
  void aCommittedEmailChangeIsNoticedAtTheReplacedAddressAndTheNoticeIsRecorded() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    var message = new MandrillMessage();
    given(
            emailService.newMandrillMessage(
                eq("old@example.com"), eq("contact_details_changed_en"), any(), any()))
        .willReturn(message);
    given(emailService.send(any(), eq(message), eq("contact_details_changed_en")))
        .willReturn(Optional.of(accepted()));

    userService.changeContactDetails(PERSONAL_CODE, "new@example.com", "+37255555555");

    assertThat(emailPersistenceService.hasPendingOrSentEmail(user, CONTACT_DETAILS_CHANGED))
        .isTrue();
  }

  @Test
  void aChangeThatFailsWhenCommittedNoticesNobody() {
    assertThatThrownBy(
            () -> userService.changeContactDetails(PERSONAL_CODE, "not-an-email", "+37255555555"))
        .isInstanceOf(TransactionSystemException.class);

    then(emailService).shouldHaveNoInteractions();
  }

  private static MandrillMessageStatus accepted() {
    return LutungGsonUtils.getGson()
        .fromJson("{\"_id\": \"notice\", \"status\": \"sent\"}", MandrillMessageStatus.class);
  }

  private StoredContactDetails storedContactDetails() {
    return jdbcClient
        .sql("SELECT email, phone_number FROM users WHERE id = :userId")
        .param("userId", user.getId())
        .query(
            (rs, rowNum) ->
                new StoredContactDetails(rs.getString("email"), rs.getString("phone_number")))
        .single();
  }

  private List<EventLog> eventLogsOfTheUser() {
    return jdbcClient
        .sql("SELECT id FROM event_log WHERE principal = :principal")
        .param("principal", PERSONAL_CODE)
        .query(Long.class)
        .list()
        .stream()
        .map(id -> eventLogRepository.findById(id).orElseThrow())
        .toList();
  }
}
