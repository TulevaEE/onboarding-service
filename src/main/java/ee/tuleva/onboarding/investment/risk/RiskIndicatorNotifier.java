package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.PublicationSnapshot;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorOutcome;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorRun;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class RiskIndicatorNotifier {

  private static final int DIGEST_BUSINESS_DAY = 4;

  private final OperationsNotificationService notificationService;
  private final RiskIndicatorDigestRepository digestRepository;
  private final RiskIndicatorPublicationRepository publicationRepository;
  private final BusinessDays businessDays;
  private final Clock clock;
  private final RiskIndicatorDigestFormatter formatter;

  void notify(RiskIndicatorRun run) {
    try {
      notifyTransitions(run);
    } catch (Exception e) {
      log.error("Failed to send risk indicator transition notification", e);
    }
    try {
      sendDigestIfDue(run);
    } catch (Exception e) {
      log.error("Failed to send risk indicator digest", e);
    }
  }

  private void notifyTransitions(RiskIndicatorRun run) {
    var lines = new ArrayList<String>();
    for (var outcome : run.outcomes()) {
      lines.addAll(transitionLines(outcome));
    }
    if (!lines.isEmpty()) {
      notificationService.sendMessage(
          "Riskiindikaatori muutus\n" + String.join("\n", lines), INVESTMENT);
    }
    becomeBaselineForNextComparison(run);
  }

  private void becomeBaselineForNextComparison(RiskIndicatorRun run) {
    var publications =
        run.outcomes().stream()
            .map(
                outcome -> {
                  var publication = outcome.publication();
                  var disclosed = formatter.disclosedClass(outcome.indicator());
                  publication.setNotified(true);
                  publication.setNotifiedDisclosedClass(
                      disclosed == null ? null : disclosed.getDisclosedClass());
                  return publication;
                })
            .toList();
    publicationRepository.saveAll(publications);
  }

  private List<String> transitionLines(RiskIndicatorOutcome outcome) {
    var indicator = outcome.indicator();
    var previous = outcome.previous();
    var disclosed = formatter.disclosedClass(indicator);
    var lines = new ArrayList<String>();

    if (previous != null && hasPublishedClassChangedSinceLastMessage(previous, indicator)) {
      lines.add(
          "⚠️ %s %s — avaldatav klass muutus %s → %s (kehtib alates %s)"
              .formatted(
                  indicator.fund(),
                  indicator.indicatorType(),
                  previous.publishedClass(),
                  indicator.publishedClass(),
                  indicator.publishedSince()));
    } else if (previous != null && hasStatusChangedSinceLastMessage(previous, indicator)) {
      lines.add(
          "%s %s %s — staatus %s → %s (arvutatud klass %s, avaldatav klass %s)"
              .formatted(
                  formatter.severity(indicator, disclosed).icon(),
                  indicator.fund(),
                  indicator.indicatorType(),
                  previous.status(),
                  indicator.status(),
                  indicator.rawLatestClass(),
                  indicator.publishedClass()));
    }

    if (isComplianceDefectRatherThanTransition(previous, disclosed, indicator)) {
      lines.add(formatter.mismatchLine(indicator, disclosed));
    }
    return lines;
  }

  private boolean isComplianceDefectRatherThanTransition(
      @Nullable PublicationSnapshot previous,
      @Nullable DisclosedRiskIndicator disclosed,
      PublishedRiskIndicator indicator) {
    return formatter.isMismatched(disclosed, indicator)
        && !isMismatchAlreadyReportedForSameDisclosedClass(previous, disclosed);
  }

  private boolean hasPublishedClassChangedSinceLastMessage(
      @Nullable PublicationSnapshot previous, PublishedRiskIndicator indicator) {
    return previous != null
        && !Objects.equals(previous.publishedClass(), indicator.publishedClass());
  }

  private boolean hasStatusChangedSinceLastMessage(
      @Nullable PublicationSnapshot previous, PublishedRiskIndicator indicator) {
    return previous != null && previous.status() != indicator.status();
  }

  private boolean isMismatchAlreadyReportedForSameDisclosedClass(
      @Nullable PublicationSnapshot previous, @Nullable DisclosedRiskIndicator disclosed) {
    return previous != null
        && disclosed != null
        && lastMessageReportedMismatchOfClass(previous, disclosed.getDisclosedClass());
  }

  private boolean lastMessageReportedMismatchOfClass(
      PublicationSnapshot previous, @Nullable Integer disclosedClass) {
    var reported = previous.notifiedDisclosedClass();
    return reported != null
        && !Objects.equals(previous.publishedClass(), reported)
        && Objects.equals(reported, disclosedClass);
  }

  private void sendDigestIfDue(RiskIndicatorRun run) {
    var today = LocalDate.now(clock);
    var month = today.withDayOfMonth(1);
    if (!businessDays.isOnOrAfterNthBusinessDayOfMonth(today, DIGEST_BUSINESS_DAY)) {
      return;
    }

    var complete = run.failures().isEmpty();
    var existing = digestRepository.findByDigestMonth(month).orElse(null);
    if (existing != null && (existing.getComplete() || !complete)) {
      return;
    }

    var claim = claimMonthBeforeSending(existing, month, complete);
    try {
      notificationService.sendMessage(formatter.digest(run), INVESTMENT);
    } catch (RuntimeException e) {
      releaseClaimOnFailedSend(claim, existing);
      throw e;
    }
  }

  private RiskIndicatorDigest claimMonthBeforeSending(
      @Nullable RiskIndicatorDigest existing, LocalDate month, boolean complete) {
    if (existing == null) {
      return digestRepository.save(
          RiskIndicatorDigest.builder().digestMonth(month).complete(complete).build());
    }
    existing.setComplete(true);
    return digestRepository.save(existing);
  }

  private void releaseClaimOnFailedSend(
      RiskIndicatorDigest claim, @Nullable RiskIndicatorDigest existing) {
    if (existing == null) {
      digestRepository.delete(claim);
      return;
    }
    claim.setComplete(false);
    digestRepository.save(claim);
  }
}
