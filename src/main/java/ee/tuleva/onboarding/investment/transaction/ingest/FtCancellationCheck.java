package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.AMBIGUOUS;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.NOTHING_TO_CANCEL;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_REPLACEMENT;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.REPLACED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.VOIDED;
import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.liveOrders;
import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.matchesOnlyAWithdrawnOrder;

import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.FtVerificationStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@NullMarked
@Component
@RequiredArgsConstructor
class FtCancellationCheck {

  private static final int REPLACEMENT_PENDING_BUSINESS_DAYS = 1;

  private final Target2Calendar target2Calendar;
  private final FtConfirmationAuditRecorder auditRecorder;
  private final Clock clock;

  FtVerification voided(FtConfirmation confirmation, List<TransactionOrder> candidates) {
    Map<String, String> details = cancellationDetails(confirmation);
    @Nullable TransactionOrder order =
        candidates.isEmpty()
            ? null
            : linked(FtOrderSelection.among(candidates, confirmation), details);
    return verification(VOIDED, details, order);
  }

  FtVerification cancellation(
      FtConfirmation confirmation, List<TransactionOrder> candidates, FtCorrections corrections) {
    Map<String, String> details = cancellationDetails(confirmation);
    List<TransactionOrder> liveOrders = liveOrders(candidates);
    if (liveOrders.isEmpty()) {
      return verification(NOTHING_TO_CANCEL, details, null);
    }
    boolean ofAWithdrawnOrder = matchesOnlyAWithdrawnOrder(confirmation, candidates, liveOrders);
    FtOrderSelection selection = FtOrderSelection.among(liveOrders, confirmation);
    @Nullable TransactionOrder order = ofAWithdrawnOrder ? null : linked(selection, details);
    if (corrections.newConfirmationsCoverEveryOrder(confirmation, liveOrders)) {
      return verification(ofAWithdrawnOrder ? NOTHING_TO_CANCEL : REPLACED, details, order);
    }
    LocalDate replacementDeadline = replacementDeadline(confirmation);
    if (!LocalDate.now(clock).isAfter(replacementDeadline)) {
      details.put("replacementPendingUntil", replacementDeadline.toString());
      return verification(PENDING_REPLACEMENT, details, order);
    }
    if (selection.matchCount() > 1) {
      details.put("ambiguousOrderCount", String.valueOf(selection.matchCount()));
      return verification(AMBIGUOUS, details, order);
    }
    details.put("replacementMissing", "no new confirmation by " + replacementDeadline);
    return verification(CANCELLED, details, order);
  }

  boolean awaitingReplacement(FtConfirmation cancellation) {
    return !LocalDate.now(clock).isAfter(replacementDeadline(cancellation));
  }

  private LocalDate replacementDeadline(FtConfirmation cancellation) {
    LocalDate firstSeen = auditRecorder.firstSeenOn(cancellation);
    LocalDate waitFrom =
        firstSeen.isAfter(cancellation.tradeDate()) ? firstSeen : cancellation.tradeDate();
    return target2Calendar.addBusinessDays(waitFrom, REPLACEMENT_PENDING_BUSINESS_DAYS);
  }

  private static Map<String, String> cancellationDetails(FtConfirmation confirmation) {
    Map<String, String> details = new LinkedHashMap<>();
    details.put("cancellationSignature", confirmation.cancellationSignature());
    return details;
  }

  private static TransactionOrder linked(FtOrderSelection selection, Map<String, String> details) {
    TransactionOrder order = selection.order();
    details.put("orderUuid", order.getOrderUuid().toString());
    return order;
  }

  private static FtVerification verification(
      FtVerificationStatus status, Map<String, String> details, @Nullable TransactionOrder order) {
    return new FtVerification(new FtConfirmationResult(status, status, Map.copyOf(details)), order);
  }
}
