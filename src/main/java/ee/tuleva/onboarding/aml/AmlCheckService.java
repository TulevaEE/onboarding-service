package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.aml.dto.AmlCheckAddCommand;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.stereotype.Service;

import java.util.List;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class AmlCheckService {

    private final AmlService amlService;
    private final UserService userService;

    public void addCheckIfMissing(Long userId, AmlCheckAddCommand command) {
        User user = userService.getById(userId);
        AmlCheck check = AmlCheck.builder()
            .user(user)
            .type(command.getType())
            .success(command.isSuccess())
            .metadata(command.getMetadata())
            .build();
        amlService.addCheckIfMissing(check);
    }

    public List<AmlCheckType> getMissingChecks(Long userId) {
        User user = userService.getById(userId);
        val checks = stream(AmlCheckType.values())
            .filter(AmlCheckType::isManual)
            .collect(toList());
        val existingChecks = amlService.getChecks(user).stream()
            .map(AmlCheck::getType)
            .collect(toList());
        checks.removeAll(existingChecks);
        if (existingChecks.contains(RESIDENCY_AUTO)) {
            checks.remove(RESIDENCY_MANUAL);
        }
        if (!existingChecks.contains(CONTACT_DETAILS)) {
            checks.add(CONTACT_DETAILS);
        }
        if (!existingChecks.contains(POLITICALLY_EXPOSED_PERSON)) {
            checks.remove(POLITICALLY_EXPOSED_PERSON);
        }
        return checks;
    }
}
