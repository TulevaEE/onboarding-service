package ee.tuleva.domain.fund;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface FundRepository extends CrudRepository<Fund, Long> {

    List<Fund> findByFundManager(FundManager fundManager);

}
