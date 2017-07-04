package ee.tuleva.onboarding.user;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UserRepository extends CrudRepository<User, Long> {

	Optional<User> findByPersonalCode(String personalCode);

  Optional<User> findByEmail(String email);

}