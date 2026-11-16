package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.kyb.KybCheckType.HIGH_RISK_NACE;
import static ee.tuleva.onboarding.kyb.KybCheckType.RELATED_PERSONS_KYC;
import static ee.tuleva.onboarding.kyb.KybScreeningTrigger.SUBMISSION;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.ON_HOLD;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.PENDING;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.REJECTED;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckPerformedEvent;
import ee.tuleva.onboarding.kyb.KybScreeningTrigger;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class LegalEntityOnboardingEventListener {

  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final ApplicationEventPublisher eventPublisher;

  @EventListener
  @Transactional
  public void onKybCheckPerformed(KybCheckPerformedEvent event) {
    var registryCode = event.getCompany().registryCode().value();
    var oldStatus =
        savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY).orElse(null);
    var failedGateChecks = failedGateChecks(event);
    var newStatus = statusFor(failedGateChecks, oldStatus, event.getTrigger());

    if (newStatus == oldStatus || oldStatus == ON_HOLD) {
      return;
    }

    if (oldStatus == COMPLETED && keepsCompleted(failedGateChecks, event)) {
      return;
    }

    var previousStatus =
        savingsFundOnboardingRepository
            .saveOnboardingStatus(registryCode, LEGAL_ENTITY, newStatus)
            .orElse(null);

    announce(event, newStatus, previousStatus);
  }

  private boolean keepsCompleted(List<KybCheck> failedGateChecks, KybCheckPerformedEvent event) {
    var failedChecksBesidesActivity = withoutActivityCheck(failedGateChecks);
    if (failedChecksBesidesActivity.isEmpty()) {
      log.info(
          "KYB high-risk business activity at an onboarded company, keeping completed status for manual review: registryCode={}, personalCode={}, failedChecks={}",
          event.getCompany().registryCode().value(),
          event.getPersonalCode().value(),
          formatFailedChecks(event.getChecks()));
      return true;
    }
    if (isInconclusiveOwnershipFailure(failedChecksBesidesActivity, event)) {
      log.error(
          "KYB ownership verification inconclusive, keeping completed status for manual review: registryCode={}, personalCode={}, failedChecks={}",
          event.getCompany().registryCode().value(),
          event.getPersonalCode().value(),
          formatFailedChecks(event.getChecks()));
      return true;
    }
    if (isUnchangedHighRiskFinding(failedChecksBesidesActivity, event)) {
      log.info(
          "KYB high-risk finding unchanged since it was reviewed, keeping completed status: registryCode={}, personalCode={}, failedChecks={}",
          event.getCompany().registryCode().value(),
          event.getPersonalCode().value(),
          formatFailedChecks(event.getChecks()));
      return true;
    }
    return false;
  }

  private void announce(
      KybCheckPerformedEvent event,
      SavingsFundOnboardingStatus newStatus,
      @Nullable SavingsFundOnboardingStatus previousStatus) {
    if (newStatus == COMPLETED) {
      log.info(
          "Legal entity onboarding completed: registryCode={}, personalCode={}, oldStatus={}",
          event.getCompany().registryCode().value(),
          event.getPersonalCode().value(),
          previousStatus);
      if (previousStatus == PENDING) {
        eventPublisher.publishEvent(
            new LegalEntityOnboardedEvent(
                this, event.getCompany().registryCode(), event.getCompany().name()));
      }
    } else if (newStatus == ON_HOLD) {
      log.info(
          "Legal entity onboarding held for review: registryCode={}, personalCode={}, oldStatus={},"
              + " failedChecks={}",
          event.getCompany().registryCode().value(),
          event.getPersonalCode().value(),
          previousStatus,
          formatFailedChecks(event.getChecks()));
    } else if (newStatus == PENDING) {
      log.info(
          "Legal entity onboarding waiting for related persons: registryCode={}, personalCode={}, oldStatus={}",
          event.getCompany().registryCode().value(),
          event.getPersonalCode().value(),
          previousStatus);
    } else if (previousStatus == COMPLETED) {
      log.error(
          "Legal entity onboarding rejected after being completed: registryCode={}, personalCode={}, failedChecks={}",
          event.getCompany().registryCode().value(),
          event.getPersonalCode().value(),
          formatFailedChecks(event.getChecks()));
    } else {
      log.info(
          "Legal entity onboarding rejected: registryCode={}, personalCode={}, oldStatus={}, failedChecks={}",
          event.getCompany().registryCode().value(),
          event.getPersonalCode().value(),
          previousStatus,
          formatFailedChecks(event.getChecks()));
    }
  }

  private SavingsFundOnboardingStatus statusFor(
      List<KybCheck> failedGateChecks,
      @Nullable SavingsFundOnboardingStatus oldStatus,
      KybScreeningTrigger trigger) {
    if (failedGateChecks.isEmpty()) {
      return COMPLETED;
    }
    if (!mayWaitOrBeHeld(oldStatus, trigger)
        || !failedGateChecks.stream().allMatch(this::waitsOrHolds)) {
      return REJECTED;
    }
    return failedGateChecks.stream().anyMatch(check -> check.type() == RELATED_PERSONS_KYC)
        ? PENDING
        : ON_HOLD;
  }

  // A completed company never waits again: softening it back to pending would re-open an account
  // that never closed and email the applicant about it. A rejected one waits only when the person
  // resubmits the survey — monitoring re-screens rejected companies too, and must not heal them.
  private static boolean mayWaitOrBeHeld(
      @Nullable SavingsFundOnboardingStatus oldStatus, KybScreeningTrigger trigger) {
    return oldStatus == null
        || oldStatus == PENDING
        || (oldStatus == REJECTED && trigger == SUBMISSION);
  }

  private boolean waitsOrHolds(KybCheck failedGateCheck) {
    return failedGateCheck.type() == RELATED_PERSONS_KYC
        || failedGateCheck.type().isHighRiskIndicator();
  }

  private List<KybCheck> failedGateChecks(KybCheckPerformedEvent event) {
    return event.getChecks().stream()
        .filter(check -> check.type().isOnboardingGate() && !check.success())
        .toList();
  }

  private static List<KybCheck> withoutActivityCheck(List<KybCheck> failedGateChecks) {
    return failedGateChecks.stream().filter(check -> check.type() != HIGH_RISK_NACE).toList();
  }

  private boolean isInconclusiveOwnershipFailure(
      List<KybCheck> failedGateChecks, KybCheckPerformedEvent event) {
    return !failedGateChecks.isEmpty()
        && failedGateChecks.stream().allMatch(check -> check.type().isOwnershipCheck())
        && !event.hasMetadataChangeFor(failedGateChecks.stream().map(KybCheck::type).toList());
  }

  private boolean isUnchangedHighRiskFinding(
      List<KybCheck> failedGateChecks, KybCheckPerformedEvent event) {
    return failedGateChecks.stream().allMatch(check -> check.type().isHighRiskIndicator())
        && !event.hasMetadataChangeFor(failedGateChecks.stream().map(KybCheck::type).toList());
  }

  private static String formatFailedChecks(List<KybCheck> checks) {
    return checks.stream()
        .filter(check -> check.type().isOnboardingGate() && !check.success())
        .map(check -> check.type().name())
        .collect(joining(","));
  }
}
