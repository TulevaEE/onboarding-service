package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PrincipalService {

    private final UserRepository userRepository;

    public AuthenticatedPerson getFrom(Person person) {

        User user = userRepository.findByPersonalCode(person.getPersonalCode());

        if (user != null && !user.getActive()) {
            log.info("Failed to login inactive user with personal code {}", person.getPersonalCode());
            throw new InvalidRequestException("INACTIVE_USER");
        }

        return AuthenticatedPerson.builder()
                .firstName(person.getFirstName())
                .lastName(person.getLastName())
                .personalCode(person.getPersonalCode())
                .user(user)
                .build();

    }

}
