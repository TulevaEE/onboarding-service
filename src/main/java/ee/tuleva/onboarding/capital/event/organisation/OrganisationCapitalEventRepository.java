package ee.tuleva.onboarding.capital.event.organisation;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.math.BigDecimal;

public interface OrganisationCapitalEventRepository extends CrudRepository<OrganisationCapitalEvent, Long> {

    @Query("select sum(e.fiatValue) from OrganisationCapitalEvent e")
    BigDecimal getTotalValue();

}
