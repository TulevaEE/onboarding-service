package ee.tuleva.onboarding.audit;

import ee.tuleva.onboarding.auth.principal.Person;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditServiceMonitor {

    private final AuditEventPublisher auditEventPublisher;

    @Before("execution(* ee.tuleva.onboarding.account.AccountStatementService.getMyPensionAccountStatement(..)) && args(person)")
    public void logServiceAccess(Person person) {
        auditEventPublisher.publish(person.getPersonalCode(), AuditEventType.GET_ACCOUNT_STATEMENT);
    }

}
