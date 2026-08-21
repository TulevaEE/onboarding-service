package ee.tuleva.onboarding.party;

import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.kyc.BeforeKycCheckedEvent;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Must stay synchronous: the guardian screening has to land before the minor's risk
// assessment (check_24 in kyc_ob_assess_user_risk) reads it in the same request.
@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
class GuardianKycScreeningListener {

  private final ParentChildLinkService parentChildLinkService;
  private final UserService userService;
  private final AmlService amlService;
  private final Clock clock;

  @EventListener
  public void beforeKycChecked(BeforeKycCheckedEvent event) {
    String subjectPersonalCode = event.person().getPersonalCode();
    if (!PersonalCode.isMinor(subjectPersonalCode, LocalDate.now(clock))) {
      return;
    }
    parentChildLinkService
        .findGuardianCodes(subjectPersonalCode)
        .forEach(guardianCode -> screenGuardian(guardianCode, subjectPersonalCode));
  }

  private void screenGuardian(String guardianPersonalCode, String childPersonalCode) {
    userService
        .findByPersonalCode(guardianPersonalCode)
        .ifPresentOrElse(
            amlService::addSanctionAndPepCheckIfMissing,
            () ->
                log.warn(
                    "Guardian has no user account, skipping sanction/PEP screening: guardianCode={}, childCode={}",
                    guardianPersonalCode,
                    childPersonalCode));
  }
}
