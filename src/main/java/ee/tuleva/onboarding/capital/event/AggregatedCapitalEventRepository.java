package ee.tuleva.onboarding.capital.event;

import org.springframework.data.repository.CrudRepository;

public interface AggregatedCapitalEventRepository extends CrudRepository<AggregatedCapitalEvent, Long> {
}
