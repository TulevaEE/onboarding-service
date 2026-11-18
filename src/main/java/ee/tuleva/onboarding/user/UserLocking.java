package ee.tuleva.onboarding.user;

import java.util.Optional;

interface UserLocking {

  Optional<User> lockForUpdate(String personalCode);
}
