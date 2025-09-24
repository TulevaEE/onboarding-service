package ee.tuleva.onboarding.capital.event;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface AggregatedCapitalEventRepository
    extends CrudRepository<AggregatedCapitalEvent, Long> {
  AggregatedCapitalEvent findTopByOrderByDateDesc();

  @Query(
      "SELECT a.ownershipUnitPrice FROM AggregatedCapitalEvent a ORDER BY a.date DESC, a.id DESC LIMIT 1")
  Optional<BigDecimal> findLatestOwnershipUnitPrice();
}
