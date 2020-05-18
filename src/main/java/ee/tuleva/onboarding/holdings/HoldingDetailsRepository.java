package ee.tuleva.onboarding.holdings;

import org.springframework.data.repository.CrudRepository;

public interface HoldingDetailsRepository extends CrudRepository<HoldingDetail, Long> {
    HoldingDetail findFirstByOrderByCreatedDateDesc();
}
