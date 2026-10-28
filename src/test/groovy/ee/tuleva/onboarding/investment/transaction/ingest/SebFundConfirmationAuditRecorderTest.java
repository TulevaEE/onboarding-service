package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationAuditRecorder.ALERTED;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus.FAR_FROM_NAV;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.ReportStatus.MATCH;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.ReportStatus.NOT_FOUND;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.TimingStatus.ON_TIME;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.RegistryComparison;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  }

  @Test
  void unalertedFindings_treatsEveryFindingAsNewWhenTheAlertHistoryCannotBeRead() {
    alertHistoryCannotBeRead();
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
                RegistryComparison.NOT_IN_REGISTRY,
                FAR_FROM_NAV,
                new BigDecimal("12.3"),
                new BigDecimal("0.8130"),
                ON_TIME,
                1));

    assertThat(recorder.unalertedFindings(outcome)).isEqualTo(List.of("NOT_FOUND", "FAR_FROM_NAV"));
  }

  @Test
  void unalertedFindings_alertsAgainWhenACorrectedConfirmationNamesAnotherInstrument() {
    SebFundConfirmationOutcome outcome = farFromNav();
    Map<String, Object> earlierAlert = new HashMap<>();
    earlierAlert.put("fund", "TUK75");
    earlierAlert.put("isin", "IE00BFG1TM61");
    earlierAlert.put("side", "BUY");
    earlierAlert.put("orderReceivedAt", "2026-01-05T09:48:17Z");
    earlierAlert.put("pricingDate", "2026-01-06");
    earlierAlert.put("valueDate", "2026-01-09");
    earlierAlert.put("quantity", "1000.00");
    earlierAlert.put("price", "12.4");
    earlierAlert.put("tradeAmount", "12400.00");
    earlierAlert.put("fee", "0.00");
    earlierAlert.put("totalAmount", "12400.00");
    earlierAlert.put("findings", List.of("FAR_FROM_NAV"));
    willReturn(List.of(TransactionAuditEvent.builder().payload(earlierAlert).build()))
        .given(auditEventRepository)
        .findByEventTypeAndDedupKey(ALERTED, "DLA0000001");

    assertThat(recorder.unalertedFindings(outcome)).isEqualTo(List.of("FAR_FROM_NAV"));

    earlierAlert.put("isin", "IE00BKPTWY98");
    assertThat(recorder.unalertedFindings(outcome)).isEmpty();
  }

  private static SebFundConfirmationOutcome farFromNav() {
    return new SebFundConfirmationOutcome(
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
            MATCH,
            List.of(),
            RegistryComparison.NOT_IN_REGISTRY,
            FAR_FROM_NAV,
            new BigDecimal("12.3"),
            new BigDecimal("0.8130"),
            ON_TIME,
            1));
  }

  @Test
  void alreadyAlerted_treatsAMissingConfirmationAsNotAlertedWhenTheAlertHistoryCannotBeRead() {
    alertHistoryCannotBeRead();
    MissingSebFundConfirmation missing =
        new MissingSebFundConfirmation(
            "DLA0000002", TUK75, "IE00BKPTWY98", BUY, LocalDate.of(2026, 1, 5), BigDecimal.TEN);

    assertThat(recorder.alreadyAlerted(missing)).isFalse();
  }

  @Test
  void alreadyAlertedToday_treatsACheckFailureAsNotAlertedWhenTheAlertHistoryCannotBeRead() {
    alertHistoryCannotBeRead();
    assertThat(
            recorder.alreadyAlertedToday(
                new SebFundConfirmationCheckFailure("puuduvate kinnituste otsing", "down")))
        .isFalse();
  }

  @Test
  void alreadyAlerted_treatsAnUnreadablePdfAsNotAlertedWhenTheAlertHistoryCannotBeRead() {
    alertHistoryCannotBeRead();
    UnreadableSebFundConfirmation unreadable =
        new UnreadableSebFundConfirmation(
            "seb-confirmations/DLA0000003-001.pdf", "Glyph not in table");

    assertThat(recorder.alreadyAlerted(unreadable)).isFalse();
  }

  @Test
  void alreadyAlerted_treatsAMisnamedPdfAsNotAlertedWhenTheAlertHistoryCannotBeRead() {
    alertHistoryCannotBeRead();
    MisnamedSebFundConfirmation misnamed =
        new MisnamedSebFundConfirmation(
            "seb-confirmations/DLA0000002-001.pdf", "DLA0000002", "DLA0000001");

    assertThat(recorder.alreadyAlerted(misnamed)).isFalse();
  }

  private void alertHistoryCannotBeRead() {
    given(auditEventRepository.findByEventTypeAndDedupKey(any(), any()))
        .willThrow(new IllegalStateException("audit store down"));
  }
}
