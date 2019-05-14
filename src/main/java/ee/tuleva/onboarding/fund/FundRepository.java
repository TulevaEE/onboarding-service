package ee.tuleva.onboarding.fund;

import org.springframework.data.repository.CrudRepository;

public interface FundRepository extends CrudRepository<Fund, Long> {

    Iterable<Fund> findByFundManagerNameIgnoreCase(String fundManagerName);

    Fund findByIsin(String isin);

}
