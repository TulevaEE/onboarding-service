package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class AmlCheckService {

    private final AmlService amlService;
    private final UserService userService;

    public void addCheckIfMissing(Long userId, AmlCheckType type, boolean result) {
        User user = userService.getById(userId);
        amlService.addCheckIfMissing(user, type, result);
    }

    public List<AmlCheckType> getMissingChecks(Long userId) {
        User user = userService.getById(userId);
        val initial = stream(AmlCheckType.values())
            .filter(AmlCheckType::isManual)
            .collect(toList());
        val existing = amlService.getChecks(user).stream()
            .map(AmlCheck::getType)
            .collect(toList());
        initial.removeAll(existing);
        if (existing.contains(AmlCheckType.RESIDENCY_AUTO)) {
            initial.remove(AmlCheckType.RESIDENCY_MANUAL);
        }
        return initial;
    }
}
