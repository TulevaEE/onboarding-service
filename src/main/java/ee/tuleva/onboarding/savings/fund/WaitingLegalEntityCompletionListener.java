package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.NONE;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import ee.tuleva.onboarding.kyb.KybCheckHistory;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyb.RegistryCode;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@NullMarked
class WaitingLegalEntityCompletionListener {

  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final LegalEntityScreener legalEntityScreener;
  private final KybCheckHistory kybCheckHistory;
  private final TransactionTemplate rescreenTransaction;

  WaitingLegalEntityCompletionListener(
      SavingsFundOnboardingRepository savingsFundOnboardingRepository,
      LegalEntityScreener legalEntityScreener,
      KybCheckHistory kybCheckHistory,
      PlatformTransactionManager transactionManager) {
    this.savingsFundOnboardingRepository = savingsFundOnboardingRepository;
    this.legalEntityScreener = legalEntityScreener;
    this.kybCheckHistory = kybCheckHistory;
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
    var personalCode = event.getPersonalCode();
    var waiting =
        savingsFundOnboardingRepository.findPendingLegalEntityCodes().stream()
            .filter(registryCode -> isWaitingFor(registryCode, personalCode))
            .toList();
    if (waiting.isEmpty()) {
      return;
    }
    log.info(
        "Re-screening companies waiting for related persons: personalCode={}, count={}",
        personalCode,
        waiting.size());
    waiting.forEach(this::rescreen);
  }

  private boolean isVerified(KycCheckPerformedEvent event) {
    var riskLevel = event.getKycCheck().riskLevel();
    return riskLevel == LOW || riskLevel == NONE;
  }

  // Screening a company costs three registry round trips, so only the companies whose latest
  // related persons check named this person are re-screened. Whenever that check cannot say whom
  // the company is waiting for, it is re-screened anyway: a gap in the metadata must not strand a
  // company, and the nightly monitoring job would pick it up regardless.
  private boolean isWaitingFor(String registryCode, String personalCode) {
    try {
      var incompleteKycPersonalCodes =
          kybCheckHistory.findIncompleteKycPersonalCodes(new RegistryCode(registryCode));
      if (incompleteKycPersonalCodes.isEmpty()) {
        log.info(
            "Waiting company names nobody with incomplete KYC, re-screening it: registryCode={}",
            registryCode);
        return true;
      }
      return incompleteKycPersonalCodes.contains(personalCode);
    } catch (Exception e) {
      log.error(
          "Failed to read whom a waiting company waits for, re-screening it: registryCode={}",
          registryCode,
          e);
      return true;
    }
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
