package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.idcard.IdCardSession;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmlAutoChecker {

    private final AmlService amlService;
    private final UserService userService;

    @EventListener
    public void onBeforeTokenGrantedEvent(BeforeTokenGrantedEvent event) {
        Person person = event.getPerson();
        Boolean isResident = isResident(event);

        User user = userService.findByPersonalCode(person.getPersonalCode())
            .orElseThrow(() -> new IllegalStateException("User not found with code " + person.getPersonalCode()));

        amlService.checkUserAfterLogin(user, person, isResident);
    }

    private Boolean isResident(BeforeTokenGrantedEvent event) {
        Object credentials = event.getAuthentication().getUserAuthentication().getCredentials();
        if (credentials instanceof IdCardSession) {
            val documentType = ((IdCardSession) credentials).documentType;
            return documentType.isResident();
        }
        return null;
    }
}
