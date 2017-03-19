package ee.tuleva.onboarding.fund;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface FundRepository extends CrudRepository<Fund, Long> {

    Fund findByNameIgnoreCase(@Param("name") String name);

    Iterable<Fund> findByFundManagerNameIgnoreCase(String fundManagerName);

    Fund findByIsin(String isin);

}
