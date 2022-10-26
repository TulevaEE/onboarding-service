package ee.tuleva.onboarding.event.broadcasting;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.event.TrackableEventPublisher;
import ee.tuleva.onboarding.event.TrackableEventType;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditEventBroadcaster {

  private final TrackableEventPublisher trackableEventPublisher;

  @Before(
      "execution(* ee.tuleva.onboarding.account.AccountStatementService.getAccountStatement(..)) && args(person)")
  public void logServiceAccess(Person person) {
    trackableEventPublisher.publish(person, TrackableEventType.GET_ACCOUNT_STATEMENT);
  }

  @Before(
      "execution(* ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider.getAccountOverview(..)) && args(person, ..)")
  public void logCashFlowAccess(Person person) {
    trackableEventPublisher.publish(person, TrackableEventType.GET_CASH_FLOWS);
  }
}
