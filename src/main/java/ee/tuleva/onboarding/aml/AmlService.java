package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AmlService {

    private final AmlCheckRepository amlCheckRepository;

    public void addCheck(User user, AmlCheckType type, Boolean success) {
        AmlCheck amlCheck = AmlCheck.builder()
            .user(user)
            .type(type)
            .success(success)
            .build();
        amlCheckRepository.save(amlCheck);
    }

    public boolean hasCheck(User user, AmlCheckType type) {
        return amlCheckRepository.exists(Example.of(AmlCheck.builder().user(user).type(type).build()));
    }
}
