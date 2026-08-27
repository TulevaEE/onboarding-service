package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.NONE;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
class WaitingLegalEntityCompletionListener {

  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final LegalEntityScreener legalEntityScreener;
  private final TransactionTemplate rescreenTransaction;

  WaitingLegalEntityCompletionListener(
      SavingsFundOnboardingRepository savingsFundOnboardingRepository,
      LegalEntityScreener legalEntityScreener,
      PlatformTransactionManager transactionManager) {
    this.savingsFundOnboardingRepository = savingsFundOnboardingRepository;
    this.legalEntityScreener = legalEntityScreener;
    this.rescreenTransaction = new TransactionTemplate(transactionManager);
    this.rescreenTransaction.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
  }

  // Screening republishes KybCheckPerformedEvent, whose transactional listeners are what complete
  // the onboarding. A failure in any of them marks the transaction they run in rollback-only, so
  // each company is re-screened after commit, in a transaction of its own.
  @TransactionalEventListener(phase = AFTER_COMMIT)
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

  private void rescreen(String registryCode) {
    try {
      rescreenTransaction.executeWithoutResult(
          status -> legalEntityScreener.screenLatest(registryCode));
    } catch (Exception e) {
      log.error("Failed to re-screen a waiting company: registryCode={}", registryCode, e);
    }
  }
}
