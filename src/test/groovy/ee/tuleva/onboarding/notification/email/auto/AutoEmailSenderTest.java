package ee.tuleva.onboarding.notification.email.auto;

import static ee.tuleva.onboarding.analytics.earlywithdrawals.AnalyticsEarlyWithdrawalFixture.anEarlyWithdrawal;
import static ee.tuleva.onboarding.analytics.leavers.ExchangeTransactionLeaverFixture.leaverFixture;
import static ee.tuleva.onboarding.analytics.leavers.ExchangeTransactionLeaverFixture.leaverFixture2;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SCHEDULED;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_LEAVERS;
import static ee.tuleva.onboarding.notification.email.auto.EmailEvent.NEW_EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.notification.email.auto.EmailEvent.NEW_LEAVER;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.*;

import ee.tuleva.onboarding.analytics.earlywithdrawals.AnalyticsEarlyWithdrawalsRepository;
import ee.tuleva.onboarding.analytics.leavers.ExchangeTransactionLeaver;
import ee.tuleva.onboarding.analytics.leavers.ExchangeTransactionLeaverFixture;
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
    var leaver = leaverFixture();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 1);

    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(leaver));
    when(withdrawalsRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of());
    when(emailPersistenceService.getLastEmailSendDate(eq(leaver), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.empty());

    // When
    autoEmailSender.sendMonthlyEmails();

    // Then
    verify(mailchimpService, times(1)).sendEvent(leaver.email(), NEW_LEAVER);
    verify(emailPersistenceService, times(1)).save(leaver, SECOND_PILLAR_LEAVERS, SCHEDULED);
  }

  @Test
  @DisplayName("Does not send leaver emails over threshold")
  void rejectsLeaverEmailsOverThreshold() {
    List<ExchangeTransactionLeaver> recentLeavers =
        Stream.generate(ExchangeTransactionLeaverFixture::leaverFixture).limit(101).toList();

    List<ExchangeTransactionLeaver> alreadyReceivedLeavers =
        Stream.generate(ExchangeTransactionLeaverFixture::leaverFixture2).limit(50).toList();

    var leavers = Stream.concat(alreadyReceivedLeavers.stream(), recentLeavers.stream()).toList();

    when(leaversRepository.fetch(eq(LocalDate.of(2019, 12, 1)), eq(LocalDate.of(2020, 1, 1))))
        .thenReturn(leavers);
    when(emailPersistenceService.getLastEmailSendDate(
            eq(leaverFixture()), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.empty());
    when(emailPersistenceService.getLastEmailSendDate(
            eq(leaverFixture2()), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.of(ZonedDateTime.now(clock).minusMonths(2).toInstant()));

    assertThrows(
        IllegalStateException.class,
        () -> autoEmailSender.sendMonthlyEmails(),
        "Too many people for monthly emails: emailType=SECOND_PILLAR_LEAVERS, estimatedSendCount=101");
  }

  @Test
  @DisplayName(
      "Allows emails where more than threshold in initial filter but only a small portion of them haven't received an email")
  void allowsEmailsMoreThanThresholdButFewHaventReceived() {
    List<ExchangeTransactionLeaver> recentLeavers =
        Stream.generate(ExchangeTransactionLeaverFixture::leaverFixture).limit(10).toList();

    List<ExchangeTransactionLeaver> alreadyReceivedLeavers =
        Stream.generate(ExchangeTransactionLeaverFixture::leaverFixture2).limit(200).toList();

    var leavers = Stream.concat(alreadyReceivedLeavers.stream(), recentLeavers.stream()).toList();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 1);

    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate))).thenReturn(leavers);
    when(emailPersistenceService.getLastEmailSendDate(
            eq(leaverFixture()), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.empty());
    when(emailPersistenceService.getLastEmailSendDate(
            eq(leaverFixture2()), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.of(ZonedDateTime.now(clock).minusMonths(2).toInstant()));

    autoEmailSender.sendMonthlyEmails();

    verify(mailchimpService, times(10)).sendEvent(leaverFixture().email(), NEW_LEAVER);
    verify(emailPersistenceService, times(10))
        .save(leaverFixture(), SECOND_PILLAR_LEAVERS, SCHEDULED);
  }

  @Test
  @DisplayName("Does not send duplicate emails if last email sent less than 4 months ago")
  void doesNotSendDuplicates() {
    // Given
    var leaver = leaverFixture();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 1);

    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(leaver));
    when(withdrawalsRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of());

    Instant recentEmailDate = ZonedDateTime.now(clock).minusMonths(2).toInstant();
    when(emailPersistenceService.getLastEmailSendDate(eq(leaver), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.of(recentEmailDate));

    // When
    autoEmailSender.sendMonthlyEmails();

    // Then
    verify(mailchimpService, never()).sendEvent(leaver.email(), NEW_LEAVER);
    verify(emailPersistenceService, never()).save(leaver, SECOND_PILLAR_LEAVERS, SCHEDULED);
  }

  @Test
  @DisplayName("Sends email if last email was sent more than 4 months ago")
  void sendsEmailIfLastEmailOlderThanFourMonths() {
    // Given
    var leaver = leaverFixture();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 1);

    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(leaver));
    when(withdrawalsRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of());

    Instant oldEmailDate = ZonedDateTime.now(clock).minusMonths(5).toInstant();
    when(emailPersistenceService.getLastEmailSendDate(eq(leaver), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.of(oldEmailDate));

    // When
    autoEmailSender.sendMonthlyEmails();

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
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 1);

    when(withdrawalsRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(earlyWithdrawal));
    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate))).thenReturn(List.of());
    when(emailPersistenceService.getLastEmailSendDate(
            eq(earlyWithdrawal), eq(SECOND_PILLAR_EARLY_WITHDRAWAL)))
        .thenReturn(Optional.empty());

    // When
    autoEmailSender.sendMonthlyEmails();

    // Then
    verify(mailchimpService, times(1)).sendEvent(earlyWithdrawal.email(), NEW_EARLY_WITHDRAWAL);
    verify(emailPersistenceService, times(1))
        .save(earlyWithdrawal, SECOND_PILLAR_EARLY_WITHDRAWAL, SCHEDULED);
  }

  @Test
  @DisplayName("Handles 'Email not found' exception gracefully")
  void handlesEmailNotFoundException() {
    // Given
    var leaver = leaverFixture();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 1);

    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(leaver));
    when(withdrawalsRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of());
    when(emailPersistenceService.getLastEmailSendDate(eq(leaver), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.empty());

    doThrow(HttpClientErrorException.NotFound.class)
        .when(mailchimpService)
        .sendEvent(leaver.email(), NEW_LEAVER);

    // When
    autoEmailSender.sendMonthlyEmails();

    // Then
    verify(mailchimpService, times(1)).sendEvent(leaver.email(), NEW_LEAVER);
    verify(emailPersistenceService, never()).save(leaver, SECOND_PILLAR_LEAVERS, SCHEDULED);
  }

  @Test
  @DisplayName("Processes multiple repositories in a single run")
  void processesMultipleRepositories() {
    // Given
    var leaver = leaverFixture();
    var earlyWithdrawal = anEarlyWithdrawal();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 1);

    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(leaver));
    when(withdrawalsRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(earlyWithdrawal));

    when(emailPersistenceService.getLastEmailSendDate(eq(leaver), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.empty());
    when(emailPersistenceService.getLastEmailSendDate(
            eq(earlyWithdrawal), eq(SECOND_PILLAR_EARLY_WITHDRAWAL)))
        .thenReturn(Optional.empty());

    // When
    autoEmailSender.sendMonthlyEmails();

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
    var leaver1 = leaverFixture();
    var leaver2 = leaverFixture();
    LocalDate expectedStartDate = LocalDate.of(2019, 12, 1);
    LocalDate expectedEndDate = LocalDate.of(2020, 1, 1);

    when(leaversRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of(leaver1, leaver2));
    when(withdrawalsRepository.fetch(eq(expectedStartDate), eq(expectedEndDate)))
        .thenReturn(List.of());

    when(emailPersistenceService.getLastEmailSendDate(
            any(ExchangeTransactionLeaver.class), eq(SECOND_PILLAR_LEAVERS)))
        .thenReturn(Optional.empty());

    // When
    autoEmailSender.sendMonthlyEmails();

    // Then
    verify(mailchimpService, times(2)).sendEvent(anyString(), eq(NEW_LEAVER));
    verify(emailPersistenceService, times(2))
        .save(any(ExchangeTransactionLeaver.class), eq(SECOND_PILLAR_LEAVERS), eq(SCHEDULED));
  }
}
