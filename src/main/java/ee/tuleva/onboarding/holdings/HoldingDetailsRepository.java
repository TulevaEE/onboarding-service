package ee.tuleva.onboarding.holdings;

import ee.tuleva.onboarding.holdings.models.HoldingDetail;
import org.springframework.data.repository.CrudRepository;

public interface HoldingDetailsRepository extends CrudRepository<HoldingDetail, Long> {
    HoldingDetail findFirstByOrderByCreatedDateDesc();
}
