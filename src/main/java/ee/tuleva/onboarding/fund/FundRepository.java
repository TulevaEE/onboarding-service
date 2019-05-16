package ee.tuleva.onboarding.fund;

import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;

public interface FundRepository extends CrudRepository<Fund, Long> {

    Iterable<Fund> findByFundManagerNameIgnoreCase(String fundManagerName);

    Fund findByIsin(String isin);

    List<Fund> findAllByPillar(Integer pillar);
}
