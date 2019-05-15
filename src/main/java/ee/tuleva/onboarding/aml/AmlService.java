package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AmlService {

    private final AmlCheckRepository amlCheckRepository;
    private final EpisService episService;

    public void checkUserAfterLogin(User user, Person person) {
        addCheckIfMissing(user, AmlCheckType.DOCUMENT, true);
        addPensionRegistryNameCheckIfMissing(user);
        addSkNameCheckIfMissing(user, person);
    }

    private void addSkNameCheckIfMissing(User user, Person person) {
        if (!hasCheck(user, AmlCheckType.SK_NAME)) {
            addCheck(user, AmlCheckType.SK_NAME,
                personDataMatches(user, person.getFirstName(), person.getLastName(), person.getPersonalCode()));
        }
    }

    private void addPensionRegistryNameCheckIfMissing(User user) {
        if (!hasCheck(user, AmlCheckType.PENSION_REGISTRY_NAME)) {
            UserPreferences userPreferences = episService.getContactDetails(user);
            addCheck(user, AmlCheckType.PENSION_REGISTRY_NAME,
                personDataMatches(user,
                    userPreferences.getFirstName(),
                    userPreferences.getLastName(),
                    userPreferences.getPersonalCode()));
        }
    }

    private boolean personDataMatches(User user, String firstName, String lastName, String personalCode) {
        if (!user.getFirstName().toLowerCase().equals(firstName.toLowerCase())) {
            return false;
        }
        if (!user.getLastName().toLowerCase().equals(lastName.toLowerCase())) {
            return false;
        }
        return user.getPersonalCode().equals(personalCode);
    }

    public void addCheckIfMissing(User user, AmlCheckType type, Boolean success) {
        if (!hasCheck(user, type)) {
            addCheck(user, type, success);
        }
    }

    private void addCheck(User user, AmlCheckType type, Boolean success) {
        log.info("Adding check {} to user {} with success {}", type, user.getId(), success);
        AmlCheck amlCheck = AmlCheck.builder()
            .user(user)
            .type(type)
            .success(success)
            .build();
        amlCheckRepository.save(amlCheck);
    }

    private boolean hasCheck(User user, AmlCheckType type) {
        return amlCheckRepository.existsByUserAndType(user, type);
    }
}
