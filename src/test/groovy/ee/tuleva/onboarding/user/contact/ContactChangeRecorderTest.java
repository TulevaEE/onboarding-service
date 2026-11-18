package ee.tuleva.onboarding.user.contact;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.user.ContactChangeSource.SELF_SERVICE;
import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static ee.tuleva.onboarding.user.ContactField.PHONE;
import static java.time.ZoneOffset.UTC;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.user.ContactChange;
import ee.tuleva.onboarding.user.ContactDetailsChanged;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ContactChangeRecorderTest {

  private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

  @Mock ContactChangeRepository repository;
  @Mock ApplicationEventPublisher eventPublisher;

  ContactChangeRecorder recorder;

  @BeforeEach
  void setUp() {
    recorder = new ContactChangeRecorder(repository, eventPublisher, Clock.fixed(NOW, UTC));
  }

  @Test
  void recordsAnEmailChange() {
    var user = sampleUserNonMember().email("new@example.com").phoneNumber("+37255555555").build();

    recorder.recordSelfServiceChange(user, "old@example.com", "+37255555555");

    var changes = List.of(new ContactChange(EMAIL, "old@example.com", "new@example.com", NOW));
    then(repository).should().saveAll(user.getId(), SELF_SERVICE, changes);
    then(eventPublisher)
        .should()
        .publishEvent(new ContactDetailsChanged(user, SELF_SERVICE, changes));
  }

  @Test
  void recordsAPhoneNumberChange() {
    var user = sampleUserNonMember().email("old@example.com").phoneNumber("+37251111111").build();

    recorder.recordSelfServiceChange(user, "old@example.com", "+37255555555");

    var changes = List.of(new ContactChange(PHONE, "+37255555555", "+37251111111", NOW));
    then(repository).should().saveAll(user.getId(), SELF_SERVICE, changes);
    then(eventPublisher)
        .should()
        .publishEvent(new ContactDetailsChanged(user, SELF_SERVICE, changes));
  }

  @Test
  void recordsNothingAndPublishesNothingWhenNeitherValueChanged() {
    var user = sampleUserNonMember().email("old@example.com").phoneNumber("+37255555555").build();

    recorder.recordSelfServiceChange(user, "old@example.com", "+37255555555");

    verifyNoInteractions(repository, eventPublisher);
  }

  @Test
  void publishesOneEventCarryingBothChangesWhenEmailAndPhoneNumberChange() {
    var user = sampleUserNonMember().email("new@example.com").phoneNumber("+37251111111").build();

    recorder.recordSelfServiceChange(user, "old@example.com", "+37255555555");

    var changes =
        List.of(
            new ContactChange(EMAIL, "old@example.com", "new@example.com", NOW),
            new ContactChange(PHONE, "+37255555555", "+37251111111", NOW));
    then(repository).should().saveAll(user.getId(), SELF_SERVICE, changes);
    then(eventPublisher)
        .should()
        .publishEvent(new ContactDetailsChanged(user, SELF_SERVICE, changes));
    then(eventPublisher).shouldHaveNoMoreInteractions();
  }

  @Test
  void recordsAFirstPhoneNumberWithNoOldValue() {
    var user = sampleUserNonMember().email("old@example.com").phoneNumber("+37255555555").build();

    recorder.recordSelfServiceChange(user, "old@example.com", null);

    var changes = List.of(new ContactChange(PHONE, null, "+37255555555", NOW));
    then(repository).should().saveAll(user.getId(), SELF_SERVICE, changes);
    then(eventPublisher)
        .should()
        .publishEvent(new ContactDetailsChanged(user, SELF_SERVICE, changes));
  }

  @Test
  void recordsARemovedPhoneNumberWithNoNewValue() {
    var user = sampleUserNonMember().email("old@example.com").phoneNumber(null).build();

    recorder.recordSelfServiceChange(user, "old@example.com", "+37255555555");

    var changes = List.of(new ContactChange(PHONE, "+37255555555", null, NOW));
    then(repository).should().saveAll(user.getId(), SELF_SERVICE, changes);
    then(eventPublisher)
        .should()
        .publishEvent(new ContactDetailsChanged(user, SELF_SERVICE, changes));
  }

  @Test
  void recordsAnEmailThatDiffersOnlyInLetterCase() {
    var user = sampleUserNonMember().email("Old@example.com").phoneNumber("+37255555555").build();

    recorder.recordSelfServiceChange(user, "old@example.com", "+37255555555");

    var changes = List.of(new ContactChange(EMAIL, "old@example.com", "Old@example.com", NOW));
    then(repository).should().saveAll(user.getId(), SELF_SERVICE, changes);
    then(eventPublisher)
        .should()
        .publishEvent(new ContactDetailsChanged(user, SELF_SERVICE, changes));
  }

  @Test
  void recordsNothingWhenAMissingPhoneNumberIsSubmittedBlank() {
    var user = sampleUserNonMember().email("old@example.com").phoneNumber("").build();

    recorder.recordSelfServiceChange(user, "old@example.com", null);

    verifyNoInteractions(repository, eventPublisher);
  }

  @Test
  void recordsNothingWhenABlankPhoneNumberBecomesMissing() {
    var user = sampleUserNonMember().email("old@example.com").phoneNumber(null).build();

    recorder.recordSelfServiceChange(user, "old@example.com", "");

    verifyNoInteractions(repository, eventPublisher);
  }

  @Test
  void recordsAPhoneNumberClearedToBlankAsRemoved() {
    var user = sampleUserNonMember().email("old@example.com").phoneNumber("").build();

    recorder.recordSelfServiceChange(user, "old@example.com", "+37255555555");

    var changes = List.of(new ContactChange(PHONE, "+37255555555", null, NOW));
    then(repository).should().saveAll(user.getId(), SELF_SERVICE, changes);
    then(eventPublisher)
        .should()
        .publishEvent(new ContactDetailsChanged(user, SELF_SERVICE, changes));
  }

  @Test
  void recordsAPhoneNumberReplacingABlankOneAsAFirstPhoneNumber() {
    var user = sampleUserNonMember().email("old@example.com").phoneNumber("+37255555555").build();

    recorder.recordSelfServiceChange(user, "old@example.com", "");

    var changes = List.of(new ContactChange(PHONE, null, "+37255555555", NOW));
    then(repository).should().saveAll(user.getId(), SELF_SERVICE, changes);
    then(eventPublisher)
        .should()
        .publishEvent(new ContactDetailsChanged(user, SELF_SERVICE, changes));
  }

  @Test
  void recordsANonBlankValueAsGivenWithoutTrimmingIt() {
    var user = sampleUserNonMember().email("old@example.com").phoneNumber(" +37251111111").build();

    recorder.recordSelfServiceChange(user, "old@example.com", "+37255555555");

    var changes = List.of(new ContactChange(PHONE, "+37255555555", " +37251111111", NOW));
    then(repository).should().saveAll(user.getId(), SELF_SERVICE, changes);
  }
}
