package ee.tuleva.onboarding.party;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChildCoParentCaptureListener {

  private final CustodyVerificationService custodyVerificationService;
  private final ParentChildLinkRegistrationService parentChildLinkRegistrationService;
  private final UserService userService;
  private final AmlService amlService;

  @Async
  @TransactionalEventListener(phase = AFTER_COMMIT)
  public void onChildOnboarded(ChildOnboardedEvent event) {
    List<String> coParentPersonalCodes;
    try {
      coParentPersonalCodes =
          custodyVerificationService.findGuardiansWithAssetManagement(
              event.childPersonalCode(), event.parentPersonalCode());
    } catch (RuntimeException e) {
      log.error(
          "Failed to look up co-parents for pending capture: parentCode={}, childCode={}",
          event.parentPersonalCode(),
          event.childPersonalCode(),
          e);
      return;
    }
    coParentPersonalCodes.forEach(coParentPersonalCode -> capture(coParentPersonalCode, event));
  }

  private void capture(String coParentPersonalCode, ChildOnboardedEvent event) {
    try {
      parentChildLinkRegistrationService.registerPending(
          coParentPersonalCode,
          event.childPersonalCode(),
          event.childFirstName(),
          event.childLastName());
    } catch (RuntimeException e) {
      log.error(
          "Failed to capture pending co-parent: coParentCode={}, childCode={}",
          coParentPersonalCode,
          event.childPersonalCode(),
          e);
      return;
    }
    screenCoParent(coParentPersonalCode, event.childPersonalCode());
  }

  private void screenCoParent(String coParentPersonalCode, String childPersonalCode) {
    try {
      userService
          .findByPersonalCode(coParentPersonalCode)
          .ifPresentOrElse(
              amlService::addSanctionAndPepCheckIfMissing,
              () ->
                  log.info(
                      "Co-parent has no user account, skipping sanction/PEP screening: coParentCode={}, childCode={}",
                      coParentPersonalCode,
                      childPersonalCode));
    } catch (RuntimeException e) {
      log.error(
          "Co-parent screening failed: coParentCode={}, childCode={}",
          coParentPersonalCode,
          childPersonalCode,
          e);
    }
  }
}
