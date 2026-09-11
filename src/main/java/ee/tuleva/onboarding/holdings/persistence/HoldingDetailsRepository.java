package ee.tuleva.onboarding.holdings.persistence;

import org.jspecify.annotations.Nullable;
import org.springframework.data.repository.CrudRepository;

public interface HoldingDetailsRepository extends CrudRepository<HoldingDetail, Long> {
  @Nullable HoldingDetail findFirstByOrderByCreatedDateDesc();
}
