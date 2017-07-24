package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.Person;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserNameUpdater {

    private final UserService userService;

    @EventListener
    public void onBeforeTokenGrantedEvent(BeforeTokenGrantedEvent event) {
        Person person = (Person) event.getAuthentication().getPrincipal();
        log.info("Updating user name: timestamp: {}, name: {} {}",
                event.getTimestamp(),
                person.getFirstName(),
                person.getLastName()
        );

        userService.findByPersonalCode(person.getPersonalCode()).ifPresent(user -> {
            user.setFirstName(person.getFirstName());
            user.setLastName(person.getLastName());
            userService.save(user);
        });
    }

}