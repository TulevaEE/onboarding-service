package ee.tuleva.onboarding.capital;

import ee.tuleva.onboarding.user.User;
import org.springframework.data.repository.CrudRepository;

public interface InitialCapitalRepository extends CrudRepository<InitialCapital, Long> {

    InitialCapital findByUser(User user);

}
