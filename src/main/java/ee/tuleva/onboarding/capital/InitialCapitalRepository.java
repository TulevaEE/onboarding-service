package ee.tuleva.onboarding.capital;

import org.springframework.data.repository.CrudRepository;

public interface InitialCapitalRepository extends CrudRepository<InitialCapital, Long> {

    InitialCapital findByUserId(Long userId);


}
