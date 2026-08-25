package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.kyb.KybCheckType.RELATED_PERSONS_KYC;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.PENDING;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.REJECTED;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckPerformedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    var personalCode = event.getPersonalCode().value();
    var oldStatus =
        savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY).orElse(null);
    var failedGateChecks = failedGateChecks(event);
    var newStatus = statusFor(failedGateChecks, oldStatus);

    if (newStatus == oldStatus) {
      return;
    }

    if (oldStatus == COMPLETED && isInconclusiveOwnershipFailure(failedGateChecks, event)) {
      log.error(
          "KYB ownership verification inconclusive, keeping completed status for manual review: registryCode={}, personalCode={}, failedChecks={}",
          registryCode,
          personalCode,
          formatFailedChecks(event.getChecks()));
      return;
    }

    savingsFundOnboardingRepository.saveOnboardingStatus(registryCode, LEGAL_ENTITY, newStatus);

    if (newStatus == COMPLETED) {
      log.info(
          "Legal entity onboarding completed: registryCode={}, personalCode={}, oldStatus={}",
          registryCode,
          personalCode,
          oldStatus);
      // Only a company that was waiting gets told: one that completed on the spot
      // has the applicant sitting in front of the success page already.
      if (oldStatus == PENDING) {
        eventPublisher.publishEvent(new LegalEntityOnboardedEvent(this, event.getCompany()));
      }
    } else if (newStatus == PENDING) {
      log.info(
          "Legal entity onboarding waiting for related persons: registryCode={}, personalCode={}, oldStatus={}",
          registryCode,
          personalCode,
          oldStatus);
    } else if (oldStatus == COMPLETED) {
      log.error(
          "Legal entity onboarding rejected after being completed: registryCode={}, personalCode={}, failedChecks={}",
          registryCode,
          personalCode,
          formatFailedChecks(event.getChecks()));
    } else {
      log.info(
          "Legal entity onboarding rejected: registryCode={}, personalCode={}, oldStatus={}, failedChecks={}",
          registryCode,
          personalCode,
          oldStatus,
          formatFailedChecks(event.getChecks()));
    }
  }

  // A company whose only outstanding gate is an unverified related person has not
  // failed, it is waiting for that person, and it completes on its own once they are
  // verified. An account that is already open is different: losing a verification
  // there is an alarm, so it still rejects.
  private SavingsFundOnboardingStatus statusFor(
      List<KybCheck> failedGateChecks, SavingsFundOnboardingStatus oldStatus) {
    if (failedGateChecks.isEmpty()) {
      return COMPLETED;
    }
    if (oldStatus != COMPLETED && onlyRelatedPersonsKycFailed(failedGateChecks)) {
      return PENDING;
    }
    return REJECTED;
  }

  private boolean onlyRelatedPersonsKycFailed(List<KybCheck> failedGateChecks) {
    return failedGateChecks.stream().allMatch(check -> check.type() == RELATED_PERSONS_KYC);
  }

  private List<KybCheck> failedGateChecks(KybCheckPerformedEvent event) {
    return event.getChecks().stream()
        .filter(check -> check.type().isOnboardingGate() && !check.success())
        .toList();
  }

  private boolean isInconclusiveOwnershipFailure(
      List<KybCheck> failedGateChecks, KybCheckPerformedEvent event) {
    return !failedGateChecks.isEmpty()
        && failedGateChecks.stream().allMatch(check -> check.type().isOwnershipCheck())
        && !event.hasMetadataChangeFor(failedGateChecks.stream().map(KybCheck::type).toList());
  }

  private static String formatFailedChecks(List<KybCheck> checks) {
    return checks.stream()
        .filter(check -> check.type().isOnboardingGate() && !check.success())
        .map(check -> check.type().name())
        .collect(joining(","));
  }
}
