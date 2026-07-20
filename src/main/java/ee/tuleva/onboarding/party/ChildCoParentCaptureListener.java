package ee.tuleva.onboarding.party;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChildCoParentCaptureListener {

  private final CustodyVerificationService custodyVerificationService;
  private final ParentChildLinkRegistrationService parentChildLinkRegistrationService;

  @TransactionalEventListener(phase = AFTER_COMMIT)
  public void onChildOnboarded(ChildOnboardedEvent event) {
    try {
      custodyVerificationService
          .findGuardiansWithAssetManagement(event.childPersonalCode(), event.parentPersonalCode())
          .forEach(
              coParentPersonalCode ->
                  parentChildLinkRegistrationService.registerPending(
                      coParentPersonalCode,
                      event.childPersonalCode(),
                      event.childFirstName(),
                      event.childLastName()));
    } catch (RuntimeException e) {
      log.warn(
          "Failed to capture pending co-parents: parentCode={}, childCode={}",
          event.parentPersonalCode(),
          event.childPersonalCode(),
          e);
    }
  }
}
