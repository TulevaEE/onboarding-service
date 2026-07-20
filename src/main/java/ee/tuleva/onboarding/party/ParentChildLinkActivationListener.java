package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.NONE;
import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.PERSONAL_ONBOARDING;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.kyc.KycCheck.RiskLevel;
import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Activates a co-parent's PENDING_KYC link at the KYC-COMPLETION boundary — the moment access should
// be granted — never at onboarding-start (which only verifies custody and seeds the pending record).
//
// Why KycCheckPerformedEvent and not SavingsFundOnboardingCompletedEvent (as originally sketched):
// the child's aggregate onboarding status flips to COMPLETED exactly once, so its completion event
// fires only for the FIRST parent. A co-parent joining a child who is already onboarded would then
// never see a completion event. KycCheckPerformedEvent fires on every KYC check, so activation works
// for each co-parent independently, and stays endpoint-agnostic.
//
// The acting/authenticated parent is not carried on the event (its subject is the child, screened
// "as the child"), so it is read from the security context — the event is published synchronously on
// the request thread. When there is no acting parent (system/test-published events) activation is
// simply skipped.
@Slf4j
@Component
@RequiredArgsConstructor
public class ParentChildLinkActivationListener {

  private final ParentChildLinkRegistrationService parentChildLinkRegistrationService;

  @EventListener
  @Transactional
  public void onKycCheckPerformed(KycCheckPerformedEvent event) {
    if (event.getPurpose() != PERSONAL_ONBOARDING || !completes(event.getKycCheck().riskLevel())) {
      return;
    }
    String childPersonalCode = event.getPersonalCode();
    actingParentPersonalCode()
        .filter(actingParent -> !actingParent.equals(childPersonalCode))
        .ifPresent(
            actingParent ->
                parentChildLinkRegistrationService.activate(actingParent, childPersonalCode));
  }

  // Mirrors SavingsFundOnboardingService.mapRiskLevelToStatus: only a LOW/NONE check completes
  // onboarding. MEDIUM (pending) and HIGH (rejected) must NOT grant access.
  private boolean completes(RiskLevel riskLevel) {
    return riskLevel == LOW || riskLevel == NONE;
  }

  private Optional<String> actingParentPersonalCode() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedPerson person) {
      return Optional.of(person.getPersonalCode());
    }
    return Optional.empty();
  }
}
