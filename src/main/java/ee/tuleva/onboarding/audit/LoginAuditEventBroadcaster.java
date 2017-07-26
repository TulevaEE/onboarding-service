package ee.tuleva.onboarding.audit;

import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.Person;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoginAuditEventBroadcaster {

    private final AuditEventPublisher auditEventPublisher;

    @EventListener
    public void onBeforeTokenGrantedEvent(BeforeTokenGrantedEvent event) {
        Person person = (Person) event.getAuthentication().getPrincipal();
        log.info("Broadcasting login audit event from BeforeTokenGrantedEvent: timestamp: {}, name: {} {}",
                event.getTimestamp(),
                person.getFirstName(),
                person.getLastName()
        );

        auditEventPublisher.publish(person.getPersonalCode(), AuditEventType.LOGIN);

    }

}