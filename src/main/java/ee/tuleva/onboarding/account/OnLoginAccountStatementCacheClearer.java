package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OnLoginAccountStatementCacheClearer {

    private final EpisService episService;

    @EventListener
    public void onBeforeTokenGrantedEvent(BeforeTokenGrantedEvent event) {
        Person person = (Person) event.getAuthentication().getPrincipal();
        log.info("On BeforeTokenGrantedEvent: timestamp: {}, name: {} {}",
                event.getTimestamp(),
                person.getFirstName(),
                person.getLastName()
        );

        episService.clearCache(person);
    }

}