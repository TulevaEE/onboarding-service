package ee.tuleva.onboarding.party;

import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.kyc.BeforeKycCheckedEvent;
import ee.tuleva.onboarding.personalcode.PersonalCode;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
class GuardianKycScreeningListener {

  private final ParentChildLinkService parentChildLinkService;
  private final UserService userService;
  private final SanctionAndPepScreener sanctionAndPepScreener;
  private final Clock clock;

  @EventListener
  public void screenGuardiansInTheSurveysTransactionBeforeTheMinorIsAssessed(
      BeforeKycCheckedEvent event) {
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
            sanctionAndPepScreener::addSanctionAndPepCheckIfMissing,
            () ->
                log.warn(
                    "Guardian has no user account, skipping sanction/PEP screening: guardianCode={}, childCode={}",
                    guardianPersonalCode,
                    childPersonalCode));
  }
}
