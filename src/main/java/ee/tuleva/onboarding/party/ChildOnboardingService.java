package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;

import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildOnboardingService {

  private final CustodyVerificationService custodyVerificationService;
  private final ParentChildLinkRegistrationService parentChildLinkRegistrationService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final AmlService amlService;

  @Transactional
  public ChildOnboardingResult onboardChild(String parentPersonalCode, String childPersonalCode) {
    CustodyVerification verification =
        custodyVerificationService.verify(parentPersonalCode, childPersonalCode);

    if (!verification.isVerified()) {
      log.info(
          "Child custody not verified, routing to ops review: parentCode={}, childCode={}, outcome={}",
          parentPersonalCode,
          childPersonalCode,
          verification.outcome());
      amlService.addCustodyRightCheck(
          childPersonalCode, false, Map.of("outcome", verification.outcome().name()));
      return ChildOnboardingResult.underReview();
    }

    PopulationRegisterPerson child = verification.child();
    parentChildLinkRegistrationService.register(
        parentPersonalCode,
        childPersonalCode,
        child.firstName(),
        child.lastName(),
        LEGAL_REPRESENTATIVE);
    savingsFundOnboardingService.seedPersonOnboardingIfAbsent(childPersonalCode);
    amlService.addCustodyRightCheck(childPersonalCode, true, verification.evidence());

    return ChildOnboardingResult.verified(child);
  }
}
