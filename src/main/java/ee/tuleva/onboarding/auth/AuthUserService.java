package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthUserService {

    private final UserRepository userRepository;

    public User getByPersonalCode(String personalCode) {
        User user = userRepository.findByPersonalCode(personalCode);

        if (user == null) {
            log.error("Failed to authenticate user: couldn't find user with personal code {}", personalCode);
            throw new InvalidRequestException("INVALID_USER_CREDENTIALS");
        }

        if (!user.getActive()) {
            log.info("Failed to login inactive user with personal code {}", personalCode);
            throw new InvalidRequestException("INACTIVE_USER");
        }
        return user;
    }

}
