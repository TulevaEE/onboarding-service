package ee.tuleva.domain.fund;

import org.springframework.data.repository.CrudRepository;

public interface FundManagerRepository extends CrudRepository<FundManager, Long> {

    FundManager findByName(String name);

}
