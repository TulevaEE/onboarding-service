package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtConfirmationType.CANCELLATION;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.AMBIGUOUS;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CONFIRMED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.NOTHING_TO_CANCEL;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ORPHAN;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_NAV;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_REPLACEMENT;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PRICE_MISSING;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.REPLACED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCHECKED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCONFIRMED;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
                + "Next: check how SEB's pending report lists these trades; a row without SEB's"
                + " Our ref is the usual reason",
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
                + "Next: check how SEB's pending report lists these trades; a row without SEB's"
                + " Our ref is the usual reason",
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
                + "Next: find which registry order the trade belongs to; cancel only a duplicate\n"
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
  void confirmationPdfThatCouldNotBeRead_isSentWithItsKeyAndWhyNot() {
    digest(true)
        .publish(
            List.of(
                new FtUnreadableConfirmation(
                    "ft-confirmations/MIDTEST0001-00_18f0c0ffee.pdf",
                    "FT confirmation PDF missing required field: field=Gross Price")));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT confirmation PDF could not be read:"
                + " key=ft-confirmations/MIDTEST0001-00_18f0c0ffee.pdf, error=FT confirmation PDF"
                + " missing required field: field=Gross Price\n"
                + "Next: check the trade in the unreadable PDF against its order by hand",
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
                + " trade date by the end of 2026-06-09\n"
                + "Next: check the price against the official close by hand",
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
  void ftTradeSebExecutedWithoutAnFtConfirmation_isSentWithItsSebPriceWithinTolerance() {
    digest(true).publish(List.of(unconfirmedTrade(OK, Map.of("referencePrice", "10.09"))));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "SEB executed an FT trade with no readable FT confirmation by the end of"
                + " 2026-06-09: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08,"
                + " quantity=40434, sebPrice=10.09 — price within tolerance of the closing price"
                + " 10.09\n"
                + "Next: ask FT for the missing confirmation",
            INVESTMENT);
  }

  @Test
  void ftTradeSebExecutedWithoutAnFtConfirmation_isSentWithHowFarItsSebPriceIsOffTheClose() {
    digest(true)
        .publish(
            List.of(
                unconfirmedTrade(
                    ERROR,
                    Map.of("referencePrice", "10.04", "priceDeltaPercent", "0.4980079681274900"))));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "SEB executed an FT trade with no readable FT confirmation by the end of"
                + " 2026-06-09: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08,"
                + " quantity=40434, sebPrice=10.09 — price 0.50% off the closing price 10.04\n"
                + "Next: ask FT for the missing confirmation",
            INVESTMENT);
  }

  @Test
  void ftTradeSebExecutedWithoutAnFtConfirmation_isSentWithAClosingPriceThatIsNotValid() {
    digest(true)
        .publish(
            List.of(
                unconfirmedTrade(
                    ERROR, Map.of("referencePrice", "0.00", "invalidReferencePrice", "true"))));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "SEB executed an FT trade with no readable FT confirmation by the end of"
                + " 2026-06-09: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08,"
                + " quantity=40434, sebPrice=10.09 — closing price 0.00 is not valid\n"
                + "Next: ask FT for the missing confirmation",
            INVESTMENT);
  }

  @Test
  void ftTradeSebExecutedWithoutAnFtConfirmation_isSentWithTheClosingPriceMissing() {
    digest(true)
        .publish(
            List.of(unconfirmedTrade(PRICE_MISSING, Map.of("closingPriceDueBy", "2026-06-09"))));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "SEB executed an FT trade with no readable FT confirmation by the end of"
                + " 2026-06-09: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08,"
                + " quantity=40434, sebPrice=10.09 — no closing price for the trade date by the"
                + " end of 2026-06-09\n"
                + "Next: ask FT for the missing confirmation\n"
                + "Next: check the price against the official close by hand",
            INVESTMENT);
  }

  @Test
  void ftTradeSebExecutedWithoutAnFtConfirmation_isSentEvenWhenItsPriceCouldNotBeChecked() {
    digest(true)
        .publish(
            List.of(unconfirmedTrade(UNCHECKED, Map.of("verificationError", "price source down"))));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "SEB executed an FT trade with no readable FT confirmation by the end of"
                + " 2026-06-09: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08,"
                + " quantity=40434, sebPrice=10.09 — price could not be checked: price source"
                + " down\n"
                + "Next: ask FT for the missing confirmation",
            INVESTMENT);
  }

  @Test
  void ftTradeSebExecutedWithoutAnFtConfirmationOrAPrice_isSentWithoutTheSebPrice() {
    digest(true)
        .publish(
            List.of(
                new FtExecutedTrade(
                    TUK75,
                    ISIN,
                    TRADE_DATE,
                    UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29"),
                    new BigDecimal("40434"),
                    null,
                    new FtConfirmationResult(
                        UNCONFIRMED,
                        UNCHECKED,
                        Map.of(
                            "confirmationDueBy",
                            "2026-06-09",
                            "verificationError",
                            "SEB reported no price for the trade")))));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "SEB executed an FT trade with no readable FT confirmation by the end of"
                + " 2026-06-09: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08,"
                + " quantity=40434, sebPrice=none — price could not be checked: SEB reported no"
                + " price for the trade\n"
                + "Next: ask FT for the missing confirmation",
            INVESTMENT);
  }

  @Test
  void nextStepsAreListedOnceAfterAllTheIssueLines_evenWhenSeveralIssuesShareOne() {
    digest(true)
        .publish(
            List.of(
                unconfirmedTrade(PRICE_MISSING, Map.of("closingPriceDueBy", "2026-06-09")),
                priceMissing(OK),
                new FtUnreadableConfirmation(
                    "ft-confirmations/MIDTEST0001-00_18f0c0ffee.pdf", "malformed")));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 3 issue(s) need attention\n"
                + "SEB executed an FT trade with no readable FT confirmation by the end of"
                + " 2026-06-09: fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08,"
                + " quantity=40434, sebPrice=10.09 — no closing price for the trade date by the"
                + " end of 2026-06-09\n"
                + "FT price could not be checked: fund=TUK75, isin=IE000F60HVH9,"
                + " tradeDate=2026-06-08, quantity=40434, price=10.09 — no closing price for the"
                + " trade date by the end of 2026-06-09\n"
                + "FT confirmation PDF could not be read:"
                + " key=ft-confirmations/MIDTEST0001-00_18f0c0ffee.pdf, error=malformed\n"
                + "Next: ask FT for the missing confirmation\n"
                + "Next: check the price against the official close by hand\n"
                + "Next: check the trade in the unreadable PDF against its order by hand",
            INVESTMENT);
  }

  @Test
  void ftTradeFtConfirmed_isNotSent() {
    digest(true)
        .publish(
            List.of(
                new FtExecutedTrade(
                    TUK75,
                    ISIN,
                    TRADE_DATE,
                    UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29"),
                    new BigDecimal("40434"),
                    new BigDecimal("10.09"),
                    new FtConfirmationResult(CONFIRMED, CONFIRMED, Map.of()))));

    verifyNoInteractions(notificationService);
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
                + " fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434\n"
                + "Next: check the cancelled trade with FT; if it did not happen, cancel the order"
                + " in the registry",
            INVESTMENT);
  }

  @Test
  void cancellationMatchingSeveralOrders_isStillSentAsACancellationLine() {
    digest(true).publish(List.of(cancellationOutcome(AMBIGUOUS)));

    verify(notificationService)
        .sendMessage(
            "FT confirmation check: 1 issue(s) need attention\n"
                + "FT cancelled a trade and sent no new confirmation of the same quantity:"
                + " fund=TUK75, isin=IE000F60HVH9, tradeDate=2026-06-08, quantity=40434\n"
                + "Next: check the cancelled trade with FT; if it did not happen, cancel the order"
                + " in the registry",
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
    verify(auditRecorder).recordAlerted(error);
  }

  @Test
  void alreadyAlertedActionableRow_isNotSentAgain() {
    given(auditRecorder.alreadyAlerted(any())).willReturn(true);

    digest(true).publish(List.of(outcome(ERROR, OK)));

    verifyNoInteractions(notificationService);
    verify(auditRecorder, never()).recordAlerted(any());
  }

  @Test
  void deliveredActionableRows_areRecordedAsAlerted() {
    FtConfirmationOutcome error = outcome(ERROR, OK);

    digest(true).publish(List.of(error));

    verify(auditRecorder).recordAlerted(error);
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
    verify(auditRecorder).recordAlerted(error);
  }

  @Test
  void slackFailure_doesNotPropagate_andDoesNotRecordAlerted() {
    willThrow(new RuntimeException("slack down"))
        .given(notificationService)
        .sendMessage(anyString(), eq(INVESTMENT));

    assertThatCode(() -> digest(true).publish(List.of(outcome(ERROR, OK))))
        .doesNotThrowAnyException();

    verify(auditRecorder, never()).recordAlerted(any());
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

  private static FtExecutedTrade unconfirmedTrade(
      FtVerificationStatus priceStatus, Map<String, String> priceDetails) {
    Map<String, String> details = new HashMap<>(priceDetails);
    details.put("confirmationDueBy", "2026-06-09");
    return new FtExecutedTrade(
        TUK75,
        ISIN,
        TRADE_DATE,
        UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29"),
        new BigDecimal("40434"),
        new BigDecimal("10.09"),
        new FtConfirmationResult(UNCONFIRMED, priceStatus, Map.copyOf(details)));
  }

  private static FtConfirmationOutcome priceMissing(FtVerificationStatus quantityStatus) {
    return new FtConfirmationOutcome(
        outcome(quantityStatus, PRICE_MISSING).confirmation(),
        new FtConfirmationResult(
            quantityStatus, PRICE_MISSING, Map.of("closingPriceDueBy", "2026-06-09")));
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
