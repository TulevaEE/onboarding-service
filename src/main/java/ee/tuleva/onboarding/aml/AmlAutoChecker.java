package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.aml.exception.AmlChecksMissingException;
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.idcard.IdCardSession;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.epis.contact.event.ContactDetailsUpdatedEvent;
import ee.tuleva.onboarding.mandate.event.BeforeMandateCreatedEvent;
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
    private final ContactDetailsService contactDetailsService;

    @EventListener
    public void beforeLogin(BeforeTokenGrantedEvent event) {
        Person person = event.getPerson();
        Boolean isResident = isResident(event);

        User user = userService.findByPersonalCode(person.getPersonalCode())
            .orElseThrow(() -> new IllegalStateException("User not found with code " + person.getPersonalCode()));

        amlService.checkUserBeforeLogin(user, person, isResident);
    }

    @EventListener
    public void afterLogin(AfterTokenGrantedEvent event) {
        Person person = event.getPerson();
        String token = event.getAccessToken().getValue();

        userService.findByPersonalCode(person.getPersonalCode()).ifPresent(user -> {
            UserPreferences contactDetails = contactDetailsService.getContactDetails(person, token);
            amlService.addPensionRegistryNameCheckIfMissing(user, contactDetails);
        });
    }

    @EventListener
    public void contactDetailsUpdated(ContactDetailsUpdatedEvent event) {
        amlService.addContactDetailsCheckIfMissing(event.getUser());
    }

    @EventListener
    public void beforeMandateCreated(BeforeMandateCreatedEvent event) {
        if (!amlService.allChecksPassed(event.getUser(), event.getPillar())) {
            throw AmlChecksMissingException.newInstance();
        }
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
