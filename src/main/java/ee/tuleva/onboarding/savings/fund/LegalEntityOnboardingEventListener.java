package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.kyb.KybCheckType.DATA_CHANGED;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.REJECTED;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckPerformedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class LegalEntityOnboardingEventListener {

  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;

  @EventListener
  @Transactional
  public void onKybCheckPerformed(KybCheckPerformedEvent event) {
    var registryCode = event.getCompany().registryCode().value();
    var personalCode = event.getPersonalCode().value();
    var oldStatus =
        savingsFundOnboardingRepository.findStatus(registryCode, LEGAL_ENTITY).orElse(null);
    var newStatus = allChecksPassed(event) ? COMPLETED : REJECTED;

    if (newStatus == oldStatus) {
      return;
    }

    savingsFundOnboardingRepository.saveOnboardingStatus(registryCode, LEGAL_ENTITY, newStatus);

    if (newStatus == COMPLETED) {
      log.info(
          "Legal entity onboarding completed: registryCode={}, personalCode={}, oldStatus={}",
          registryCode,
          personalCode,
          oldStatus);
    } else {
      log.info(
          "Legal entity onboarding rejected: registryCode={}, personalCode={}, oldStatus={}, failedChecks={}",
          registryCode,
          personalCode,
          oldStatus,
          formatFailedChecks(event.getChecks()));
    }
  }

  private boolean allChecksPassed(KybCheckPerformedEvent event) {
    return event.getChecks().stream()
        .filter(check -> check.type() != DATA_CHANGED)
        .allMatch(KybCheck::success);
  }

  private static String formatFailedChecks(List<KybCheck> checks) {
    return checks.stream()
        .filter(check -> check.type() != DATA_CHANGED && !check.success())
        .map(check -> check.type().name())
        .collect(joining(","));
  }
}
