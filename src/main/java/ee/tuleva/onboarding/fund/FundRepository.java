package ee.tuleva.onboarding.fund;

import ee.tuleva.onboarding.fund.Fund.FundStatus;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface FundRepository extends CrudRepository<Fund, Long> {

  Iterable<Fund> findAllByFundManagerNameIgnoreCase(String fundManagerName);

  Fund findByIsin(String isin);

  List<Fund> findAllByPillar(Integer pillar);

  List<Fund> findAllByPillarAndStatus(Integer pillar, FundStatus status);
}
