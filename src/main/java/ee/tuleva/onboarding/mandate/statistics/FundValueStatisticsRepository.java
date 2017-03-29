package ee.tuleva.onboarding.mandate.statistics;

import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface FundValueStatisticsRepository extends CrudRepository<FundValueStatistics, Long> {

    List<FundValueStatistics> findByIdentifier(UUID sampleStatisticsIdentifier);

}
