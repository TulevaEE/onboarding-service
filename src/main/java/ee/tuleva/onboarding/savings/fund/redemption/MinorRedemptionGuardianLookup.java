package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import ee.tuleva.onboarding.party.MinorGuardianLookupService;
import ee.tuleva.onboarding.savings.fund.notification.RedemptionRequestedEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinorRedemptionGuardianLookup {

  private final MinorGuardianLookupService minorGuardianLookupService;
  private final UserService userService;

  @Async
  @TransactionalEventListener(phase = AFTER_COMMIT)
  public void onRedemptionRequested(RedemptionRequestedEvent event) {
    if (event.party().type() != PERSON) {
      return;
    }
    try {
      userService
          .getById(event.userId())
          .map(User::getPersonalCode)
          .filter(requester -> !requester.equals(event.party().code()))
          .ifPresent(
              parent -> minorGuardianLookupService.lookUpChildOnce(parent, event.party().code()));
    } catch (RuntimeException e) {
      log.error(
          "Guardian lookup at a minor's redemption failed: redemptionRequestId={}, error={}",
          event.redemptionRequestId(),
          e.getClass().getSimpleName());
    }
  }
}
