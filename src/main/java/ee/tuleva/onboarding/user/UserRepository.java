package ee.tuleva.onboarding.user;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, Long> {

  Optional<User> findByPersonalCode(String personalCode);

  Optional<User> findByEmail(String email);
}
