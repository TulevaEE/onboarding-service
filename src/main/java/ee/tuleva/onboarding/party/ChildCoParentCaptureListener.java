package ee.tuleva.onboarding.party;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.populationregister.Guardian;
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
  private final SanctionAndPepScreener sanctionAndPepScreener;

  @Async
  @TransactionalEventListener(phase = AFTER_COMMIT)
  public void onChildOnboarded(ChildOnboardedEvent event) {
    List<Guardian> coParents;
    try {
      coParents =
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
    coParents.forEach(coParent -> capture(coParent, event));
  }

  private void capture(Guardian coParent, ChildOnboardedEvent event) {
    try {
      parentChildLinkRegistrationService.registerPending(
          coParent, event.childPersonalCode(), event.childFirstName(), event.childLastName());
    } catch (RuntimeException e) {
      log.error(
          "Failed to capture pending co-parent: coParentCode={}, childCode={}",
          coParent.personalCode(),
          event.childPersonalCode(),
          e);
      return;
    }
    screenCoParent(coParent, event.childPersonalCode());
  }

  private void screenCoParent(Guardian coParent, String childPersonalCode) {
    try {
      userService
          .findByPersonalCode(coParent.personalCode())
          .ifPresentOrElse(
              sanctionAndPepScreener::addSanctionAndPepCheckIfMissing,
              () -> screenCoParentWithoutUserAccount(coParent, childPersonalCode));
    } catch (RuntimeException e) {
      log.error(
          "Co-parent screening failed: coParentCode={}, childCode={}",
          coParent.personalCode(),
          childPersonalCode,
          e);
    }
  }

  private void screenCoParentWithoutUserAccount(Guardian coParent, String childPersonalCode) {
    String firstName = coParent.firstName();
    String lastName = coParent.lastName();
    if (firstName == null || lastName == null) {
      log.warn(
          "Co-parent has no user account or registered name, skipping sanction/PEP screening: coParentCode={}, childCode={}",
          coParent.personalCode(),
          childPersonalCode);
      return;
    }
    var person = new PersonImpl(coParent.personalCode(), firstName, lastName);
    sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(
        person, sanctionAndPepScreener.recordedCitizenships(person));
  }
}
