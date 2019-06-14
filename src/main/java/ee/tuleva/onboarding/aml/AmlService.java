package ee.tuleva.onboarding.aml;

import com.google.common.collect.Lists;
import ee.tuleva.onboarding.audit.AuditEventPublisher;
import ee.tuleva.onboarding.audit.AuditEventType;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static java.util.stream.Collectors.toSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class AmlService {

    private final AmlCheckRepository amlCheckRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final List<List<AmlCheckType>> allowedCombinations = Lists.newArrayList(
        newArrayList(POLITICALLY_EXPOSED_PERSON, SK_NAME, DOCUMENT, RESIDENCY_AUTO),
        newArrayList(POLITICALLY_EXPOSED_PERSON, SK_NAME, DOCUMENT, RESIDENCY_MANUAL),
        newArrayList(POLITICALLY_EXPOSED_PERSON, PENSION_REGISTRY_NAME, DOCUMENT, RESIDENCY_AUTO),
        newArrayList(POLITICALLY_EXPOSED_PERSON, PENSION_REGISTRY_NAME, DOCUMENT, RESIDENCY_MANUAL)
    );

    public void checkUserAfterLogin(User user, Person person) {
        addCheckIfMissing(user, DOCUMENT, true);
        addSkNameCheckIfMissing(user, person);
    }

    private void addSkNameCheckIfMissing(User user, Person person) {
        if (!hasCheck(user, SK_NAME)) {
            addCheck(user, SK_NAME,
                personDataMatches(user, person.getFirstName(), person.getLastName(), person.getPersonalCode()));
        }
    }

    public void addPensionRegistryNameCheckIfMissing(User user, UserPreferences userPreferences) {
        if (!hasCheck(user, PENSION_REGISTRY_NAME)) {
            addCheck(user, PENSION_REGISTRY_NAME,
                personDataMatches(user,
                    userPreferences.getFirstName(),
                    userPreferences.getLastName(),
                    userPreferences.getPersonalCode()));
        }
    }

    private boolean personDataMatches(User user, String firstName, String lastName, String personalCode) {
        if (!StringUtils.equalsIgnoreCase(user.getFirstName(), firstName)) {
            return false;
        }
        if (!StringUtils.equalsIgnoreCase(user.getLastName(), lastName)) {
            return false;
        }
        return StringUtils.equalsIgnoreCase(user.getPersonalCode(), personalCode);
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

    public List<AmlCheck> getChecks(User user) {
        return amlCheckRepository.findAllByUser(user);
    }

    public boolean allChecksPassed(Mandate mandate) {
        val user = mandate.getUser();
        if (mandate.getPillar() == 2) {
            // No checks needed for second pillar
            return true;
        } else if (mandate.getPillar() == 3) {
            val checks = amlCheckRepository.findAllByUser(user);
            val successfulTypes = checks.stream()
                .filter(AmlCheck::isSuccess)
                .map(AmlCheck::getType)
                .collect(toSet());
            if (allowedCombinations.stream().anyMatch(successfulTypes::containsAll)) {
                return true;
            }
        }
        log.error("All necessary AML checks not passed for user {}!", user.getId());
        auditEventPublisher.publish(user.getEmail(), AuditEventType.MANDATE_DENIED);
        return false;
    }
}
