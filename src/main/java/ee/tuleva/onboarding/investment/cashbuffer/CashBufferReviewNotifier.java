package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.NotRun;
import ee.tuleva.onboarding.investment.cashbuffer.FundReviewOutcome.Reviewed;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class CashBufferReviewNotifier {

  private static final BigDecimal PERCENT = new BigDecimal("100");
  private static final String INDENT = "  ";

  private final OperationsNotificationService notificationService;

  void notify(YearMonth reviewMonth, List<FundReviewOutcome> outcomes) {
    var sustainedDrifts = reviews(outcomes).filter(CashBufferReview::driftSustained).toList();
    var unrecognisedPayouts =
        reviews(outcomes)
            .filter(review -> review.reviewMonthFlows().unrecognisedPayouts() > 0)
            .toList();
    var notRun =
        outcomes.stream().filter(NotRun.class::isInstance).map(NotRun.class::cast).toList();
    if (sustainedDrifts.isEmpty() && unrecognisedPayouts.isEmpty() && notRun.isEmpty()) {
      log.info("Cash buffer review has nothing to report: reviewMonth={}", reviewMonth);
      return;
    }
    var severity = unrecognisedPayouts.isEmpty() && notRun.isEmpty() ? INFO : ERROR;
    var message =
        Stream.of(
                Stream.of(header(reviewMonth)),
                section(
                    "LIMIT DRIFTED FROM THE RECOMMENDATION — review the reserve",
                    sustainedDrifts.stream().flatMap(CashBufferReviewNotifier::driftLines)),
                section(
                    "PAYOUT REASON NOT RECOGNISED — left out of the buffer until it is mapped in"
                        + " RegistrarPayoutReason",
                    unrecognisedPayouts.stream().map(CashBufferReviewNotifier::unrecognisedLine)),
                section(
                    "REVIEW COULD NOT RUN",
                    notRun.stream().map(CashBufferReviewNotifier::notRunLine)))
            .flatMap(lines -> lines)
            .collect(joining("\n"));
    try {
      notificationService.sendMessage(message, INVESTMENT, severity);
    } catch (RuntimeException e) {
      log.error("Cash buffer review notification failed: reviewMonth={}", reviewMonth, e);
    }
  }

  private static Stream<CashBufferReview> reviews(List<FundReviewOutcome> outcomes) {
    return outcomes.stream()
        .filter(Reviewed.class::isInstance)
        .map(Reviewed.class::cast)
        .map(Reviewed::review);
  }

  private static String header(YearMonth reviewMonth) {
    return ("CASH BUFFER REVIEW %s — the recommended day-to-day operating buffer (recurring and"
            + " one-off payouts; PEVA, RAVA and PIK cycle outflows left out), not the fund's total"
            + " cash requirement. The job recommends only; investment_fund_limit is never changed"
            + " by it.")
        .formatted(reviewMonth);
  }

  private static Stream<String> section(String title, Stream<String> lines) {
    var indented = lines.map(line -> INDENT + line).toList();
    return indented.isEmpty() ? Stream.empty() : Stream.concat(Stream.of(title), indented.stream());
  }

  private static Stream<String> driftLines(CashBufferReview review) {
    var hardDrift = review.hardDrift();
    return Stream.concat(
        review.softDrift().sustained() ? Stream.of(softDriftLine(review)) : Stream.empty(),
        hardDrift != null && hardDrift.sustained()
            ? Stream.of(hardDriftLine(review, hardDrift))
            : Stream.empty());
  }

  private static String softDriftLine(CashBufferReview review) {
    var recommendation = review.recommendation();
    var model = recommendation.model();
    var drift = review.softDrift();
    return ("%s reserve_soft: recommended %s EUR, configured %s EUR since %s, apart by %s EUR"
            + " (threshold %s EUR) for %d consecutive months — %s monthly outflow %s less %s × %s"
            + " monthly inflow %s, plus accrued fees %s; %s")
        .formatted(
            review.fund(),
            eur(recommendation.recommendedSoft()),
            eur(review.configured().reserveSoft()),
            review.configured().effectiveDate(),
            eur(drift.divergence()),
            eur(drift.threshold()),
            drift.consecutiveRuns(),
            percentile(model.outflowPercentile()),
            eur(recommendation.outflowAtPercentile()),
            model.inflowCredit().stripTrailingZeros().toPlainString(),
            percentile(model.inflowPercentile()),
            eur(recommendation.inflowAtPercentile()),
            eur(recommendation.accruedFees()),
            window(review));
  }

  private static String hardDriftLine(CashBufferReview review, Drift drift) {
    var recommendation = review.recommendation();
    var model = recommendation.model();
    return ("%s reserve_hard: recommended %s EUR, configured %s EUR since %s, apart by %s EUR"
            + " (threshold %s EUR) for %d consecutive months — %s outflow over %d business days"
            + " %s, plus accrued fees %s; %s")
        .formatted(
            review.fund(),
            eur(recommendation.recommendedHard()),
            eur(requireNonNull(review.configured().reserveHard())),
            review.configured().effectiveDate(),
            eur(drift.divergence()),
            eur(drift.threshold()),
            drift.consecutiveRuns(),
            percentile(model.outflowPercentile()),
            model.settlementHorizonDays(),
            eur(recommendation.horizonOutflowAtPercentile()),
            eur(recommendation.accruedFees()),
            window(review));
  }

  private static String unrecognisedLine(CashBufferReview review) {
    var flows = review.reviewMonthFlows();
    return "%s: %d registrar payout(s), %s EUR booked in %s"
        .formatted(
            review.fund(),
            flows.unrecognisedPayouts(),
            eur(flows.unrecognisedOutflow()),
            flows.month());
  }

  private static String notRunLine(NotRun notRun) {
    return "%s: %s (%s)"
        .formatted(notRun.fund(), notRun.reason().getDescription(), notRun.detail());
  }

  private static String window(CashBufferReview review) {
    var window = review.window();
    return "window %s..%s (%d months)"
        .formatted(window.firstMonth(), window.lastMonth(), window.depth());
  }

  private static String percentile(BigDecimal fraction) {
    return "P" + fraction.multiply(PERCENT).stripTrailingZeros().toPlainString();
  }

  private static String eur(BigDecimal amount) {
    return amount.setScale(2, HALF_UP).toPlainString();
  }
}
