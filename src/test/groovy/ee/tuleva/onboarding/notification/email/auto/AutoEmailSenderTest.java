package ee.tuleva.onboarding.notification.email.auto;

import static ee.tuleva.onboarding.analytics.earlywithdrawals.AnalyticsEarlyWithdrawalFixture.anEarlyWithdrawal;
import static ee.tuleva.onboarding.analytics.leavers.ExchangeTransactionLeaverFixture.aLeaverWith;
import static ee.tuleva.onboarding.analytics.leavers.ExchangeTransactionLeaverFixture.anotherLeaverWith;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SCHEDULED;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_LEAVERS;
import static ee.tuleva.onboarding.notification.email.auto.EmailEvent.NEW_EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.notification.email.auto.EmailEvent.NEW_LEAVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.*;

import ee.tuleva.onboarding.analytics.earlywithdrawals.AnalyticsEarlyWithdrawalsRepository;
import ee.tuleva.onboarding.analytics.leavers.ExchangeTransactionLeaver;
import ee.tuleva.onboarding.analytics.leavers.ExchangeTransactionLeaversRepository;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.provider.MailchimpService;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class AutoEmailSenderTest {

  private Clock clock;

  @Mock private ExchangeTransactionLeaversRepository leaversRepository;

  @Mock private AnalyticsEarlyWithdrawalsRepository withdrawalsRepository;

  @Mock private MailchimpService mailchimpService;

  @Mock private EmailPersistenceService emailPersistenceService;

  private AutoEmailSender autoEmailSender;

  @BeforeEach
  void setUp() {
    clock = TestClockHolder.clock;

    when(leaversRepository.getEmailType()).thenReturn(SECOND_PILLAR_LEAVERS);
    when(withdrawalsRepository.getEmailType()).thenReturn(SECOND_PILLAR_EARLY_WITHDRAWAL);

    autoEmailSender =
        new AutoEmailSender(
            clock,
            List.of(leaversRepository, withdrawalsRepository),
            mailchimpService,
            emailPersistenceService);
  }

  @Test
  @DisplayName("Sends leaver emails")
  void sendsLeaverEmails() {
    // Given
    var leaver = aLeaverWith().build();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 2);

    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(leaver));
    when(withdrawalsRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of());
    when(emailPersistenceService.getLastEmailSendDate(eq(leaver), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.empty());
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(true);
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_EARLY_WITHDRAWAL)))
        .thenReturn(true);

    // When
    autoEmailSender.sendAutoEmails();

    // Then
    verify(mailchimpService, times(1)).sendEvent(leaver.email(), NEW_LEAVER);
    verify(emailPersistenceService, times(1)).save(leaver, SECOND_PILLAR_LEAVERS, SCHEDULED);
  }

  @Test
  @DisplayName("Does not send leaver emails over threshold, skips that email type")
  void skipsLeaverEmailsOverThreshold() {
    List<ExchangeTransactionLeaver> recentLeavers =
        Stream.generate(() -> aLeaverWith().build()).limit(201).toList();

    List<ExchangeTransactionLeaver> alreadyReceivedLeavers =
        Stream.generate(() -> anotherLeaverWith().build()).limit(50).toList();

    var leavers = Stream.concat(alreadyReceivedLeavers.stream(), recentLeavers.stream()).toList();

    when(leaversRepository.fetch(eq(LocalDate.of(2019, 12, 1)), eq(LocalDate.of(2020, 1, 2))))
        .thenReturn(leavers);
    when(withdrawalsRepository.fetch(eq(LocalDate.of(2019, 12, 1)), eq(LocalDate.of(2020, 1, 2))))
        .thenReturn(List.of());
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(true);

    // When
    autoEmailSender.sendAutoEmails();

    // Then - should not send any emails for SECOND_PILLAR_LEAVERS due to threshold
    verify(mailchimpService, never()).sendEvent(anyString(), eq(NEW_LEAVER));
    verify(emailPersistenceService, never()).save(any(), eq(SECOND_PILLAR_LEAVERS), any());
  }

  @Test
  @DisplayName(
      "Allows emails where more than threshold in initial filter but only a small portion of them haven't received an email")
  void allowsEmailsMoreThanThresholdButFewHaventReceived() {
    List<ExchangeTransactionLeaver> recentLeavers =
        Stream.generate(() -> aLeaverWith().build()).limit(10).toList();

    List<ExchangeTransactionLeaver> alreadyReceivedLeavers =
        Stream.generate(() -> anotherLeaverWith().build()).limit(200).toList();

    var leavers = Stream.concat(alreadyReceivedLeavers.stream(), recentLeavers.stream()).toList();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 2);

    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate))).thenReturn(leavers);

    for (ExchangeTransactionLeaver leaver : recentLeavers) {
      when(emailPersistenceService.getLastEmailSendDate(eq(leaver), eq(SECOND_PILLAR_LEAVERS)))
          .thenReturn(Optional.empty());
    }
    for (ExchangeTransactionLeaver leaver : alreadyReceivedLeavers) {
      when(emailPersistenceService.getLastEmailSendDate(eq(leaver), eq(SECOND_PILLAR_LEAVERS)))
          .thenReturn(Optional.of(ZonedDateTime.now(clock).minusMonths(2).toInstant()));
    }

    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(true);
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_EARLY_WITHDRAWAL)))
        .thenReturn(true);

    autoEmailSender.sendAutoEmails();

    verify(mailchimpService, times(10)).sendEvent(anyString(), eq(NEW_LEAVER));
    verify(emailPersistenceService, times(10))
        .save(any(ExchangeTransactionLeaver.class), eq(SECOND_PILLAR_LEAVERS), eq(SCHEDULED));
  }

  @Test
  @DisplayName("Does not send duplicate emails if last email sent less than 4 months ago")
  void doesNotSendDuplicates() {
    // Given
    var leaver = aLeaverWith().build();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 2);

    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(leaver));
    when(withdrawalsRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of());

    Instant recentEmailDate = ZonedDateTime.now(clock).minusMonths(2).toInstant();
    when(emailPersistenceService.getLastEmailSendDate(eq(leaver), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.of(recentEmailDate));
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(true);
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_EARLY_WITHDRAWAL)))
        .thenReturn(true);

    // When
    autoEmailSender.sendAutoEmails();

    // Then
    verify(mailchimpService, never()).sendEvent(leaver.email(), NEW_LEAVER);
    verify(emailPersistenceService, never()).save(leaver, SECOND_PILLAR_LEAVERS, SCHEDULED);
  }

  @Test
  @DisplayName("Sends email if last email was sent more than 4 months ago")
  void sendsEmailIfLastEmailOlderThanFourMonths() {
    // Given
    var leaver = aLeaverWith().build();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 2);

    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(leaver));
    when(withdrawalsRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of());

    Instant oldEmailDate = ZonedDateTime.now(clock).minusMonths(5).toInstant();
    when(emailPersistenceService.getLastEmailSendDate(eq(leaver), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.of(oldEmailDate));
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(true);
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_EARLY_WITHDRAWAL)))
        .thenReturn(true);

    // When
    autoEmailSender.sendAutoEmails();

    // Then
    verify(mailchimpService, times(1)).sendEvent(leaver.email(), NEW_LEAVER);
    verify(emailPersistenceService, times(1)).save(leaver, SECOND_PILLAR_LEAVERS, SCHEDULED);
  }

  @Test
  @DisplayName("Sends withdrawal emails")
  void sendsWithdrawalEmails() {
    // Given
    var earlyWithdrawal = anEarlyWithdrawal();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 2);

    when(withdrawalsRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(earlyWithdrawal));
    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate))).thenReturn(List.of());
    when(emailPersistenceService.getLastEmailSendDate(
            eq(earlyWithdrawal), eq(SECOND_PILLAR_EARLY_WITHDRAWAL)))
        .thenReturn(Optional.empty());
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(true);
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_EARLY_WITHDRAWAL)))
        .thenReturn(true);

    // When
    autoEmailSender.sendAutoEmails();

    // Then
    verify(mailchimpService, times(1)).sendEvent(earlyWithdrawal.email(), NEW_EARLY_WITHDRAWAL);
    verify(emailPersistenceService, times(1))
        .save(earlyWithdrawal, SECOND_PILLAR_EARLY_WITHDRAWAL, SCHEDULED);
  }

  @Test
  @DisplayName("Handles 'Email not found' exception gracefully")
  void handlesEmailNotFoundException() {
    // Given
    var leaver = aLeaverWith().build();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 2);

    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(leaver));
    when(withdrawalsRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of());
    when(emailPersistenceService.getLastEmailSendDate(eq(leaver), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.empty());
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(true);
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_EARLY_WITHDRAWAL)))
        .thenReturn(true);

    doThrow(HttpClientErrorException.NotFound.class)
        .when(mailchimpService)
        .sendEvent(leaver.email(), NEW_LEAVER);

    // When
    autoEmailSender.sendAutoEmails();

    // Then
    verify(mailchimpService, times(1)).sendEvent(leaver.email(), NEW_LEAVER);
    verify(emailPersistenceService, never()).save(leaver, SECOND_PILLAR_LEAVERS, SCHEDULED);
  }

  @Test
  @DisplayName("Processes multiple repositories in a single run")
  void processesMultipleRepositories() {
    // Given
    var leaver = aLeaverWith().build();
    var earlyWithdrawal = anEarlyWithdrawal();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 2);

    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(leaver));
    when(withdrawalsRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(earlyWithdrawal));

    when(emailPersistenceService.getLastEmailSendDate(eq(leaver), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.empty());
    when(emailPersistenceService.getLastEmailSendDate(
            eq(earlyWithdrawal), eq(SECOND_PILLAR_EARLY_WITHDRAWAL)))
        .thenReturn(Optional.empty());
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(true);
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_EARLY_WITHDRAWAL)))
        .thenReturn(true);

    // When
    autoEmailSender.sendAutoEmails();

    // Then
    verify(mailchimpService, times(1)).sendEvent(leaver.email(), NEW_LEAVER);
    verify(mailchimpService, times(1)).sendEvent(earlyWithdrawal.email(), NEW_EARLY_WITHDRAWAL);
    verify(emailPersistenceService, times(1)).save(leaver, SECOND_PILLAR_LEAVERS, SCHEDULED);
    verify(emailPersistenceService, times(1))
        .save(earlyWithdrawal, SECOND_PILLAR_EARLY_WITHDRAWAL, SCHEDULED);
  }

  @Test
  @DisplayName("Processes multiple entries from the same repository")
  void processesMultipleEntriesFromSameRepository() {
    // Given
    var leaver1 = aLeaverWith().build();
    var leaver2 = anotherLeaverWith().build();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 2);

    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(leaver1, leaver2));
    when(withdrawalsRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of());

    when(emailPersistenceService.getLastEmailSendDate(
            any(ExchangeTransactionLeaver.class), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.empty());
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(true);
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_EARLY_WITHDRAWAL)))
        .thenReturn(true);

    // When
    autoEmailSender.sendAutoEmails();

    // Then
    verify(mailchimpService, times(2)).sendEvent(anyString(), eq(NEW_LEAVER));
    verify(emailPersistenceService, times(2))
        .save(any(ExchangeTransactionLeaver.class), eq(SECOND_PILLAR_LEAVERS), eq(SCHEDULED));
  }

  @Test
  @DisplayName("Allows first-time emails up to 1000 recipients")
  void allowsFirstTimeEmailsUpTo1000Recipients() {
    // Given - 600 recipients for a first-time email
    List<ExchangeTransactionLeaver> recentLeavers =
        Stream.generate(() -> aLeaverWith().build()).limit(600).toList();

    when(leaversRepository.fetch(eq(LocalDate.of(2019, 12, 1)), eq(LocalDate.of(2020, 1, 2))))
        .thenReturn(recentLeavers);
    when(withdrawalsRepository.fetch(eq(LocalDate.of(2019, 12, 1)), eq(LocalDate.of(2020, 1, 2))))
        .thenReturn(List.of());
    when(emailPersistenceService.getLastEmailSendDate(
            any(ExchangeTransactionLeaver.class), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.empty());
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(false); // First-time email
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_EARLY_WITHDRAWAL)))
        .thenReturn(true);

    // When
    autoEmailSender.sendAutoEmails();

    // Then - should send all 600 emails
    verify(mailchimpService, times(600)).sendEvent(anyString(), eq(NEW_LEAVER));
    verify(emailPersistenceService, times(600))
        .save(any(ExchangeTransactionLeaver.class), eq(SECOND_PILLAR_LEAVERS), eq(SCHEDULED));
  }

  @Test
  @DisplayName("Skips first-time emails over 1000 recipients")
  void skipsFirstTimeEmailsOver1000Recipients() {
    // Given - 1001 recipients for a first-time email (over 1000 limit)
    List<ExchangeTransactionLeaver> recentLeavers =
        Stream.generate(() -> aLeaverWith().build()).limit(1001).toList();

    when(leaversRepository.fetch(eq(LocalDate.of(2019, 12, 1)), eq(LocalDate.of(2020, 1, 2))))
        .thenReturn(recentLeavers);
    when(withdrawalsRepository.fetch(eq(LocalDate.of(2019, 12, 1)), eq(LocalDate.of(2020, 1, 2))))
        .thenReturn(List.of());
    when(emailPersistenceService.getLastEmailSendDate(
            any(ExchangeTransactionLeaver.class), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.empty());
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(false); // First-time email

    // When
    autoEmailSender.sendAutoEmails();

    // Then - should not send any emails (over 1000 limit)
    verify(mailchimpService, never()).sendEvent(anyString(), eq(NEW_LEAVER));
    verify(emailPersistenceService, never())
        .save(any(ExchangeTransactionLeaver.class), eq(SECOND_PILLAR_LEAVERS), any());
  }

  @Test
  @DisplayName("Passes tomorrow as end date to repository for inclusive date comparison")
  void passesTomorrowAsEndDateToRepository() {
    var leaver = aLeaverWith().build();
    when(leaversRepository.fetch(any(), any())).thenReturn(List.of(leaver));
    when(emailPersistenceService.getLastEmailSendDate(any(), any())).thenReturn(Optional.empty());
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(any())).thenReturn(true);
    when(withdrawalsRepository.fetch(any(), any())).thenReturn(List.of());

    autoEmailSender.sendAutoEmails();

    ArgumentCaptor<LocalDate> startDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LocalDate> endDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
    verify(leaversRepository).fetch(startDateCaptor.capture(), endDateCaptor.capture());

    LocalDate actualStartDate = startDateCaptor.getValue();
    LocalDate actualEndDate = endDateCaptor.getValue();
    LocalDate expectedStartDateFirstOfLastMonth = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDateTomorrow = LocalDate.of(2020, 1, 2);

    assertThat(actualStartDate).isEqualTo(expectedStartDateFirstOfLastMonth);
    assertThat(actualEndDate).isEqualTo(expectedEndDateTomorrow);
  }

  @Test
  @DisplayName("Sends email for transaction created on current day")
  void sendsEmailForTransactionCreatedToday() {
    LocalDate today = LocalDate.of(2020, 1, 1);
    LocalDate tomorrow = today.plusDays(1);
    var leaverCreatedToday = aLeaverWith().dateCreated(today).lastEmailSentDate(null).build();

    when(leaversRepository.fetch(any(), any())).thenReturn(List.of(leaverCreatedToday));
    when(emailPersistenceService.getLastEmailSendDate(any(), any())).thenReturn(Optional.empty());
    when(emailPersistenceService.hasEmailTypeBeenSentBefore(any())).thenReturn(true);
    when(withdrawalsRepository.fetch(any(), any())).thenReturn(List.of());

    autoEmailSender.sendAutoEmails();

    verify(mailchimpService, times(1)).sendEvent(any(), any());

    ArgumentCaptor<LocalDate> endDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
    verify(leaversRepository).fetch(any(), endDateCaptor.capture());

    LocalDate actualEndDate = endDateCaptor.getValue();

    assertThat(actualEndDate).isEqualTo(tomorrow);
  }
}
