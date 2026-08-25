package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.NONE;
import static ee.tuleva.onboarding.kyc.KycCheckPerformedEventOrder.COMPLETE_WAITING_COMPANIES;

import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class WaitingLegalEntityCompletionListener {

  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final LegalEntityScreener legalEntityScreener;

  // A company waiting on an unverified related person has no way of noticing that
  // the person has since verified, so every waiting company is re-screened when
  // anyone becomes verified. Screening republishes KybCheckPerformedEvent, which is
  // what actually completes the onboarding.
  @Order(COMPLETE_WAITING_COMPANIES)
  @EventListener
  @Transactional
  public void onKycCheckPerformed(KycCheckPerformedEvent event) {
    if (!isVerified(event)) {
      return;
    }
    var waiting = savingsFundOnboardingRepository.findPendingLegalEntityCodes();
    if (waiting.isEmpty()) {
      return;
    }
    log.info("Re-screening companies waiting for related persons: count={}", waiting.size());
    waiting.forEach(this::rescreen);
  }

  private boolean isVerified(KycCheckPerformedEvent event) {
    var riskLevel = event.getKycCheck().riskLevel();
    return riskLevel == LOW || riskLevel == NONE;
  }

  // One company failing to re-screen must not stop the others from completing.
  private void rescreen(String registryCode) {
    try {
      legalEntityScreener.screenLatest(registryCode);
    } catch (Exception e) {
      log.error("Failed to re-screen a waiting company: registryCode={}", registryCode, e);
    }
  }
}
