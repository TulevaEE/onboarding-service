package ee.tuleva.onboarding.account;

import ee.eesti.xtee6.kpr.PersonalSelectionResponseType;
import ee.tuleva.onboarding.kpr.KPRClient;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionsFundService {

    private final KPRClient kprClient;

    public ContributionsFundName getActiveFundName(User user) {
        log.info("Getting active fund for user id {}", user.getId());
        PersonalSelectionResponseType csdPersonalSelection = kprClient.personalSelection(user.getPersonalCode());
        String fundName = csdPersonalSelection.getPensionAccount().getSecurityName();
        log.info("Active fund for user id {} is {}", user.getId(), fundName);
        return ContributionsFundName.builder().name(fundName).build();
    }

}
