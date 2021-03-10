package ee.tuleva.onboarding.capital.event.organisation;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface OrganisationCapitalEventRepository
    extends CrudRepository<OrganisationCapitalEvent, Long> {

  @Query("select sum(e.fiatValue) from OrganisationCapitalEvent e")
  BigDecimal getTotalValue();
}
