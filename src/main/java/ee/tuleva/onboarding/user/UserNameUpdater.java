package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.Person;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static org.apache.commons.lang3.text.WordUtils.capitalizeFully;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserNameUpdater {

    private final UserService userService;

    @EventListener
    public void onBeforeTokenGrantedEvent(BeforeTokenGrantedEvent event) {
        Person person = (Person) event.getAuthentication().getPrincipal();
        String firstName = capitalizeFully(person.getFirstName());
        String lastName = capitalizeFully(person.getLastName());

        log.info("Updating user name: timestamp: {}, name: {} {}",
                event.getTimestamp(),
                firstName,
                lastName
        );

        userService.findByPersonalCode(person.getPersonalCode()).ifPresent(user -> {
            user.setFirstName(firstName);
            user.setLastName(lastName);
            userService.save(user);
        });
    }

}