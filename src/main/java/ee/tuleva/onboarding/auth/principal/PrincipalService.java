package ee.tuleva.onboarding.auth.principal;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.text.WordUtils;
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PrincipalService {

    private final UserService userService;

    public AuthenticatedPerson getFrom(Person person) {

        Optional<User> userOptional = userService.findByPersonalCode(person.getPersonalCode());

        User user = userOptional.orElseGet(() -> createUser(person));

        if (!user.getActive()) {
            log.info("Failed to login inactive user with personal code {}", person.getPersonalCode());
            throw new InvalidRequestException("INACTIVE_USER");
        }

        return AuthenticatedPerson.builder()
                .firstName(person.getFirstName())
                .lastName(person.getLastName())
                .personalCode(person.getPersonalCode())
                .userId(user.getId())
                .build();

    }

    private User createUser(Person person) {
        return userService.createNewUser(
                User.builder()
                        .firstName(WordUtils.capitalizeFully(person.getFirstName()))
                        .lastName(WordUtils.capitalizeFully(person.getLastName()))
                        .personalCode(person.getPersonalCode())
                        .active(true)
                        .build()
        );
    }

}
