package ee.tuleva.onboarding.holdings.persistence;

import org.springframework.data.repository.CrudRepository;

public interface HoldingDetailsRepository extends CrudRepository<HoldingDetail, Long> {
    HoldingDetail findFirstByOrderByCreatedDateDesc();
}
