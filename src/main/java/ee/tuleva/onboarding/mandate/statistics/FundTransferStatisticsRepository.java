package ee.tuleva.onboarding.mandate.statistics;

import org.springframework.data.repository.CrudRepository;

public interface FundTransferStatisticsRepository extends CrudRepository<FundTransferStatistics, Long> {

    public FundTransferStatistics findOneByIsin(String isin);

}
