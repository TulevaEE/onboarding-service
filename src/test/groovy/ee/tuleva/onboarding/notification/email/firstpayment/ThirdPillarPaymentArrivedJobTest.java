package ee.tuleva.onboarding.notification.email.firstpayment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.FirstThirdPillarPayment;
import ee.tuleva.onboarding.analytics.transaction.thirdpillar.FirstThirdPillarPaymentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ThirdPillarPaymentArrivedJobTest {

  private static final String FIRST_PAYER = TestPersonalCodes.withValidChecksum("3860101000");
  private static final String SECOND_PAYER = TestPersonalCodes.withValidChecksum("3850101000");

  private final FirstThirdPillarPaymentRepository repository =
      mock(FirstThirdPillarPaymentRepository.class);
  private final ThirdPillarPaymentArrivedEmailService emailService =
      mock(ThirdPillarPaymentArrivedEmailService.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC);

  private ThirdPillarPaymentArrivedJob job(boolean dryRun, int maxRecipients) {
    return new ThirdPillarPaymentArrivedJob(
        repository, emailService, clock, dryRun, maxRecipients, LocalDate.parse("2022-01-01"), 30);
  }

  private FirstThirdPillarPayment payment(String personalCode) {
    return new FirstThirdPillarPayment(
        personalCode,
        "First",
        "Last",
        "first.last@example.com",
        "EST",
        new BigDecimal("100.00"),
        LocalDate.parse("2026-08-16"),
        true,
        true,
        true,
        false,
        false,
        false);
  }

  @Test
  void refusesToRunWhenTransactionHistoryIsTooShallow() {
    given(repository.oldestOwnPaymentDate()).willReturn(Optional.of(LocalDate.parse("2024-01-01")));

    job(false, 200).run();

    verify(emailService, never()).send(any());
  }

  @Test
  void refusesToRunWhenThereAreNoTransactionsAtAll() {
    given(repository.oldestOwnPaymentDate()).willReturn(Optional.empty());

    job(false, 200).run();

    verify(emailService, never()).send(any());
  }

  @Test
  void refusesToRunWhenTheAudienceExceedsTheRecipientCap() {
    given(repository.oldestOwnPaymentDate()).willReturn(Optional.of(LocalDate.parse("2020-01-01")));
    given(
            repository.fetchUnemailedFirstPayments(
                LocalDate.parse("2026-07-19"), LocalDate.parse("2008-08-18")))
        .willReturn(List.of(payment(FIRST_PAYER), payment(SECOND_PAYER)));

    job(false, 1).run();

    verify(emailService, never()).send(any());
  }

  @Test
  void fetchesButDoesNotSendOnADryRun() {
    given(repository.oldestOwnPaymentDate()).willReturn(Optional.of(LocalDate.parse("2020-01-01")));
    given(
            repository.fetchUnemailedFirstPayments(
                LocalDate.parse("2026-07-19"), LocalDate.parse("2008-08-18")))
        .willReturn(List.of(payment(FIRST_PAYER)));

    job(true, 200).run();

    verify(emailService, never()).send(any());
  }

  @Test
  void oneFailingRecipientDoesNotStopTheOthers() {
    var failing = payment(FIRST_PAYER);
    var succeeding = payment(SECOND_PAYER);
    given(repository.oldestOwnPaymentDate()).willReturn(Optional.of(LocalDate.parse("2020-01-01")));
    given(
            repository.fetchUnemailedFirstPayments(
                LocalDate.parse("2026-07-19"), LocalDate.parse("2008-08-18")))
        .willReturn(List.of(failing, succeeding));
    given(emailService.send(failing)).willThrow(new RuntimeException("mandrill exploded"));
    given(emailService.send(succeeding)).willReturn(true);

    job(false, 200).run();

    verify(emailService, times(1)).send(succeeding);
  }
}
