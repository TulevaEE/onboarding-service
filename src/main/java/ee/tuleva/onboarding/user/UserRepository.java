package ee.tuleva.onboarding.user;

import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, Long> {

	User findByPersonalCode(String personalCode);

}