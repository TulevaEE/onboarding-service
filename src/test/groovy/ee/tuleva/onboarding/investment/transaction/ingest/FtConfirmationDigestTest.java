package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.AMBIGUOUS;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ORPHAN;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_NAV;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.FtVerificationStatus;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FtConfirmationDigestTest {

  private static final String ISIN = "IE000F60HVH9";
  private static final String OTHER_ISIN = "IE000I9HGDZ3";
  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 6, 8);

  @Mock private OperationsNotificationService notificationService;
  @Mock private FtConfirmationAuditRecorder auditRecorder;

  private FtConfirmationDigest digest(boolean registryAuthoritative) {
    return new FtConfirmationDigest(notificationService, auditRecorder, registryAuthoritative);
  }

  @Test
  void missingExecutionAfterSebsReport_isSentWithItsCauseAndOneNextStep() {
    digest(true)
        .publish(
            List.of(
                outcome(
                    ERROR,
                    OK,
                    Map.of(
                        "orderQuantity", "40434",
                        "executionMissing",
                            "no execution after SEB pending report as of 2026-06-09",
                        "sebReportAsOf", "2026-06-09"))));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434"
                + " — no execution in the registry after SEB pending report as of 2026-06-09\n"
                + "Next: check SEB's pending report for these trades; the registry records an"
                + " execution only from a row that carries SEB's Our ref",
            INVESTMENT);
  }

  @Test
  void severalRowsWithOneCause_shareOneNextStep() {
    Map<String, String> missing =
        Map.of(
            "executionMissing", "no execution after SEB pending report as of 2026-06-09",
            "sebReportAsOf", "2026-06-09");

    digest(true)
        .publish(
            List.of(outcome(ISIN, ERROR, OK, missing), outcome(OTHER_ISIN, ERROR, OK, missing)));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 2 issue(s) need attention\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434"
                + " — no execution in the registry after SEB pending report as of 2026-06-09\n"
                + "FT issue: fund=TUK75, isin=IE000I9HGDZ3, tradeDate=2026-06-08, quantity=40434"
                + " — no execution in the registry after SEB pending report as of 2026-06-09\n"
                + "Next: check SEB's pending report for these trades; the registry records an"
                + " execution only from a row that carries SEB's Our ref",
            INVESTMENT);
  }

  @Test
  void sebReportNeverReconciled_isSentAsAReportProblem() {
    digest(true)
        .publish(
            List.of(
                outcome(
                    ERROR,
                    OK,
                    Map.of(
                        "executionMissing",
                        "no SEB pending report as of 2026-06-09 or later reconciled by 2026-06-10",
                        "sebReportMissingAsOf",
                        "2026-06-09"))));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434"
                + " — no SEB pending report as of 2026-06-09 or later reconciled\n"
                + "Next: check that SEB's pending reports arrive and are imported",
            INVESTMENT);
  }

  @Test
  void quantityAndPriceBothWrong_nameBothCausesAndBothNextSteps() {
    digest(true)
        .publish(
            List.of(
                outcome(
                    ERROR,
                    ERROR,
                    Map.of(
                        "orderQuantity", "40432",
                        "orderQuantityMismatch", "40432",
                        "executedQuantity", "40434",
                        "referencePrice", "10.20",
                        "priceDeltaPercent", "1.078431372549019607843137254901961"))));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434"
                + " — order quantity 40432; price 10.09 vs reference 10.20 (1.08%)\n"
                + "Next: correct the order in the registry, or have FT correct the confirmation\n"
                + "Next: check the price with FT",
            INVESTMENT);
  }

  @Test
  void eachRemainingCause_isNamedWithItsNextStep() {
    digest(true)
        .publish(
            List.of(
                outcome(ISIN, ORPHAN, ORPHAN, Map.of("orphanReason", "no matching order")),
                outcome(ISIN, AMBIGUOUS, AMBIGUOUS, Map.of("ambiguousOrderCount", "2")),
                outcome(ISIN, ERROR, OK, Map.of("orderQuantityMissing", "order has no quantity")),
                outcome(OTHER_ISIN, ERROR, OK, Map.of("executedQuantityMismatch", "40436")),
                outcome(
                    OTHER_ISIN,
                    OK,
                    ERROR,
                    Map.of("referencePrice", "0", "invalidReferencePrice", "true"))));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 5 issue(s) need attention\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434"
                + " — no FT order in the registry for this trade\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434"
                + " — 2 registry orders match\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434"
                + " — the order has no quantity\n"
                + "FT issue: fund=TUK75, isin=IE000I9HGDZ3, tradeDate=2026-06-08, quantity=40434"
                + " — executed quantity 40436\n"
                + "FT issue: fund=TUK75, isin=IE000I9HGDZ3, tradeDate=2026-06-08, quantity=40434"
                + " — reference price 0 is not positive\n"
                + "Next: import the batch's orders into the registry\n"
                + "Next: leave one live order for the trade in the registry\n"
                + "Next: fill in the order's quantity in the registry\n"
                + "Next: compare FT's confirmation with SEB's pending report\n"
                + "Next: check the instrument's price source",
            INVESTMENT);
  }

  @Test
  void errorRowWithoutAKnownCause_isSentWithItsStatuses() {
    digest(true).publish(List.of(outcome(ERROR, OK)));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434,"
                + " quantityStatus=ERROR, priceStatus=OK",
            INVESTMENT);
  }

  @Test
  void ambiguousRow_whenRegistryAuthoritative_isSent() {
    digest(true).publish(List.of(outcome(AMBIGUOUS, AMBIGUOUS)));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434,"
                + " quantityStatus=AMBIGUOUS, priceStatus=AMBIGUOUS",
            INVESTMENT);
  }

  @Test
  void orphanRow_whenRegistryAuthoritative_isSent() {
    digest(true).publish(List.of(outcome(ORPHAN, ORPHAN)));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434,"
                + " quantityStatus=ORPHAN, priceStatus=ORPHAN",
            INVESTMENT);
  }

  @Test
  void cancellationRow_whenRegistryAuthoritative_isSentAsCancellationLine() {
    digest(true).publish(List.of(outcome(CANCELLED, CANCELLED)));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT broker cancellation: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08,"
                + " quantity=40434 — verify and cancel in registry",
            INVESTMENT);
  }

  @Test
  void whileRegistryNotAuthoritative_nothingIsSentOrRecorded_evenForActionableRows() {
    digest(false)
        .publish(
            List.of(outcome(ERROR, OK), outcome(AMBIGUOUS, AMBIGUOUS), outcome(ORPHAN, ORPHAN)));

    verifyNoInteractions(notificationService, auditRecorder);
  }

  @Test
  void cleanAndPendingRows_areNotSent() {
    digest(true).publish(List.of(outcome(OK, OK), outcome(OK, PENDING_NAV)));

    verifyNoInteractions(notificationService);
  }

  @Test
  void emptyOutcomes_areNotSent() {
    digest(true).publish(List.of());

    verifyNoInteractions(notificationService);
  }

  @Test
  void duplicateActionableRowsInOneBatch_areAlertedOnce() {
    FtConfirmationOutcome error = outcome(ERROR, OK);

    digest(true).publish(List.of(error, error));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434,"
                + " quantityStatus=ERROR, priceStatus=OK",
            INVESTMENT);
    verify(auditRecorder).recordAlerted(error.confirmation(), error.result());
  }

  @Test
  void alreadyAlertedActionableRow_isNotSentAgain() {
    given(auditRecorder.alreadyAlerted(any(), any())).willReturn(true);

    digest(true).publish(List.of(outcome(ERROR, OK)));

    verifyNoInteractions(notificationService);
    verify(auditRecorder, never()).recordAlerted(any(), any());
  }

  @Test
  void deliveredActionableRows_areRecordedAsAlerted() {
    FtConfirmationOutcome error = outcome(ERROR, OK);

    digest(true).publish(List.of(error));

    verify(auditRecorder).recordAlerted(error.confirmation(), error.result());
  }

  @Test
  void issueUnalertedInShadowMode_isAlertedOnceAfterFlagFlips() {
    FtConfirmationOutcome error = outcome(ERROR, OK);

    // Shadow mode: recorded elsewhere, but nothing is alerted or marked as alerted.
    digest(false).publish(List.of(error));
    verifyNoInteractions(notificationService, auditRecorder);

    // Flag flips: the still-open issue was never alerted, so it alerts exactly once now.
    digest(true).publish(List.of(error));
    verify(notificationService).sendMessage(anyString(), eq(INVESTMENT));
    verify(auditRecorder).recordAlerted(error.confirmation(), error.result());
  }

  @Test
  void slackFailure_doesNotPropagate_andDoesNotRecordAlerted() {
    willThrow(new RuntimeException("slack down"))
        .given(notificationService)
        .sendMessage(anyString(), eq(INVESTMENT));

    assertThatCode(() -> digest(true).publish(List.of(outcome(ERROR, OK))))
        .doesNotThrowAnyException();

    verify(auditRecorder, never()).recordAlerted(any(), any());
  }

  private static FtConfirmationOutcome outcome(
      FtVerificationStatus quantityStatus, FtVerificationStatus priceStatus) {
    return outcome(quantityStatus, priceStatus, Map.of());
  }

  private static FtConfirmationOutcome outcome(
      FtVerificationStatus quantityStatus,
      FtVerificationStatus priceStatus,
      Map<String, String> details) {
    return outcome(ISIN, quantityStatus, priceStatus, details);
  }

  private static FtConfirmationOutcome outcome(
      String isin,
      FtVerificationStatus quantityStatus,
      FtVerificationStatus priceStatus,
      Map<String, String> details) {
    FtConfirmation confirmation =
        new FtConfirmation(
            TUK75, isin, TRADE_DATE, new BigDecimal("40434"), new BigDecimal("10.09"));
    return new FtConfirmationOutcome(
        confirmation, new FtConfirmationResult(quantityStatus, priceStatus, details));
  }
}
