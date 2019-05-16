package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.auth.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.idcard.IdCardSession;
import ee.tuleva.onboarding.auth.principal.Person;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static org.apache.commons.lang3.text.WordUtils.capitalizeFully;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserDetailsUpdater {

    private final UserService userService;
    private final AmlService amlService;

    @EventListener
    public void onBeforeTokenGrantedEvent(BeforeTokenGrantedEvent event) {
        Person person = (Person) event.getAuthentication().getPrincipal();
        String firstName = capitalizeFully(person.getFirstName());
        String lastName = capitalizeFully(person.getLastName());
        Object credentials = event.getAuthentication().getUserAuthentication().getCredentials();
        Boolean resident = isResident(credentials);

        log.info("Updating user name: timestamp: {}, name: {} {} resident: {}",
            event.getTimestamp(),
            firstName,
            lastName,
            resident
        );

        userService.findByPersonalCode(person.getPersonalCode()).ifPresent(user -> {
            if (resident != null) {
                amlService.addCheckIfMissing(user, AmlCheckType.RESIDENCY_AUTO, resident);
            }
            user.setFirstName(firstName);
            user.setLastName(lastName);
            userService.save(user);
        });
    }

    private Boolean isResident(Object credentials) {
        if (credentials instanceof IdCardSession) {
            val documentType = ((IdCardSession) credentials).documentType;
            return documentType.isResident();
        }
        return null;
    }

}