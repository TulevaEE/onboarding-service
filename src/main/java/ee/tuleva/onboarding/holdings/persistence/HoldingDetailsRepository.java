package ee.tuleva.onboarding.holdings.persistence;

import ee.tuleva.onboarding.holdings.HoldingDetail;
import org.springframework.data.repository.CrudRepository;

public interface HoldingDetailsRepository extends CrudRepository<HoldingDetail, Long> {
}
