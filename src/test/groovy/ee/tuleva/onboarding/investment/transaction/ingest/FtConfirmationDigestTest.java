package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtConfirmationType.CANCELLATION;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.AMBIGUOUS;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.NOTHING_TO_CANCEL;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ORPHAN;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_NAV;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_REPLACEMENT;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PRICE_MISSING;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.REPLACED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCHECKED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.VOIDED;
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
  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 6, 8);

  @Mock private OperationsNotificationService notificationService;
  @Mock private FtConfirmationAuditRecorder auditRecorder;

  private FtConfirmationDigest digest(boolean registryAuthoritative) {
    return new FtConfirmationDigest(notificationService, auditRecorder, registryAuthoritative);
  }

  @Test
  void errorRow_whenRegistryAuthoritative_isSentAsOneInvestmentDigest() {
    digest(true).publish(List.of(outcome(ERROR, OK)));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434,"
                + " quantityStatus=ERROR, priceStatus=OK",
            INVESTMENT);
  }

  @Test
  void confirmationThatCouldNotBeChecked_isSentWithItsError() {
    digest(true).publish(List.of(unchecked(outcome(OK, OK))));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT confirmation could not be checked: fund=TUK75, isin=IE000F60HVH9,"
                + " tradeDate=2026-06-08, quantity=40434, error=price source down",
            INVESTMENT);
  }

  @Test
  void cancellationThatCouldNotBeChecked_saysSoInsteadOfThatNoNewConfirmationCame() {
    digest(true).publish(List.of(unchecked(cancellationOutcome(CANCELLED))));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT confirmation could not be checked: fund=TUK75, isin=IE000F60HVH9,"
                + " tradeDate=2026-06-08, quantity=40434, error=price source down",
            INVESTMENT);
  }

  @Test
  void priceThatCouldNotBeChecked_isSentWithTheDayTheClosingPriceWasDue() {
    digest(true).publish(List.of(priceMissing(OK)));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT price could not be checked: fund=TUK75, isin=IE000F60HVH9,"
                + " tradeDate=2026-06-08, quantity=40434, price=10.09 — no closing price for the"
                + " trade date by the end of 2026-06-09; check the price against the official"
                + " close by hand",
            INVESTMENT);
  }

  @Test
  void priceThatCouldNotBeChecked_withAQuantityError_isSentAsAnFtIssueWithBothStatuses() {
    digest(true).publish(List.of(priceMissing(ERROR)));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT issue: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434,"
                + " quantityStatus=ERROR, priceStatus=PRICE_MISSING",
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
  void cancellationWithNoNewConfirmation_whenRegistryAuthoritative_isSentAsCancellationLine() {
    digest(true).publish(List.of(cancellationOutcome(CANCELLED)));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT cancelled a trade and sent no new confirmation of the same quantity:"
                + " fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434"
                + " — check with FT;"
                + " if the trade did not happen, cancel the order in the registry",
            INVESTMENT);
  }

  @Test
  void cancellationMatchingSeveralOrders_isStillSentAsACancellationLine() {
    digest(true).publish(List.of(cancellationOutcome(AMBIGUOUS)));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT cancelled a trade and sent no new confirmation of the same quantity:"
                + " fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434"
                + " — check with FT;"
                + " if the trade did not happen, cancel the order in the registry",
            INVESTMENT);
  }

  @Test
  void ftCorrections_voidedReplacedPendingOrWithNothingToCancel_areNotSent() {
    digest(true)
        .publish(
            List.of(
                outcome(VOIDED, VOIDED),
                outcome(REPLACED, REPLACED),
                outcome(PENDING_REPLACEMENT, PENDING_REPLACEMENT),
                outcome(NOTHING_TO_CANCEL, NOTHING_TO_CANCEL)));

    verifyNoInteractions(notificationService);
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

  private static FtConfirmationOutcome cancellationOutcome(FtVerificationStatus status) {
    FtConfirmation cancellation =
        new FtConfirmation(
            TUK75,
            ISIN,
            TRADE_DATE,
            new BigDecimal("40434"),
            new BigDecimal("10.09"),
            CANCELLATION,
            "FT-ACC");
    return new FtConfirmationOutcome(
        cancellation, new FtConfirmationResult(status, status, Map.of()));
  }

  private static FtConfirmationOutcome unchecked(FtConfirmationOutcome outcome) {
    return new FtConfirmationOutcome(
        outcome.confirmation(),
        new FtConfirmationResult(
            UNCHECKED, UNCHECKED, Map.of("verificationError", "price source down")));
  }

  private static FtConfirmationOutcome priceMissing(FtVerificationStatus quantityStatus) {
    return new FtConfirmationOutcome(
        outcome(quantityStatus, PRICE_MISSING).confirmation(),
        new FtConfirmationResult(
            quantityStatus, PRICE_MISSING, Map.of("closingPriceDueBy", "2026-06-09")));
  }

  private static FtConfirmationOutcome outcome(
      FtVerificationStatus quantityStatus, FtVerificationStatus priceStatus) {
    FtConfirmation confirmation =
        new FtConfirmation(
            TUK75, ISIN, TRADE_DATE, new BigDecimal("40434"), new BigDecimal("10.09"));
    return new FtConfirmationOutcome(
        confirmation, new FtConfirmationResult(quantityStatus, priceStatus, Map.of()));
  }
}
