package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus.FAR_FROM_NAV;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.ReportStatus.NOT_FOUND;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.TimingStatus.ON_TIME;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebFundConfirmationAuditRecorderTest {

  @Mock private TransactionAuditEventRepository auditEventRepository;

  private SebFundConfirmationAuditRecorder recorder;

  @BeforeEach
  void setUp() {
    recorder =
        new SebFundConfirmationAuditRecorder(
            auditEventRepository,
            Clock.fixed(Instant.parse("2026-01-08T10:00:00Z"), ZoneOffset.UTC));
    given(auditEventRepository.findByEventTypeAndDedupKey(any(), any()))
        .willThrow(new IllegalStateException("audit store down"));
  }

  @Test
  void unalertedFindings_treatsEveryFindingAsNewWhenTheAlertHistoryCannotBeRead() {
    SebFundConfirmationOutcome outcome =
        new SebFundConfirmationOutcome(
            new SebFundConfirmation(
                TUK75,
                "DLA0000001",
                Instant.parse("2026-01-05T09:48:17Z"),
                LocalDate.of(2026, 1, 6),
                LocalDate.of(2026, 1, 9),
                "IE00BKPTWY98",
                BUY,
                new BigDecimal("1000.00"),
                new BigDecimal("12.4"),
                new BigDecimal("12400.00"),
                new BigDecimal("0.00"),
                new BigDecimal("12400.00")),
            new SebFundConfirmationResult(
                NOT_FOUND,
                List.of(),
                FAR_FROM_NAV,
                new BigDecimal("12.3"),
                new BigDecimal("0.8130"),
                ON_TIME,
                1));

    assertThat(recorder.unalertedFindings(outcome)).isEqualTo(List.of("NOT_FOUND", "FAR_FROM_NAV"));
  }

  @Test
  void alreadyAlerted_treatsAMissingConfirmationAsNotAlertedWhenTheAlertHistoryCannotBeRead() {
    MissingSebFundConfirmation missing =
        new MissingSebFundConfirmation(
            "DLA0000002", TUK75, "IE00BKPTWY98", BUY, LocalDate.of(2026, 1, 5), BigDecimal.TEN);

    assertThat(recorder.alreadyAlerted(missing)).isFalse();
  }

  @Test
  void alreadyAlertedToday_treatsACheckFailureAsNotAlertedWhenTheAlertHistoryCannotBeRead() {
    assertThat(
            recorder.alreadyAlertedToday(
                new SebFundConfirmationCheckFailure("puuduvate kinnituste otsing", "down")))
        .isFalse();
  }

  @Test
  void alreadyAlerted_treatsAnUnreadablePdfAsNotAlertedWhenTheAlertHistoryCannotBeRead() {
    UnreadableSebFundConfirmation unreadable =
        new UnreadableSebFundConfirmation(
            "seb-confirmations/DLA0000003-001.pdf", "Glyph not in table");

    assertThat(recorder.alreadyAlerted(unreadable)).isFalse();
  }
}
