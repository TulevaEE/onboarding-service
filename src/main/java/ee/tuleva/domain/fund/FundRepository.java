package ee.tuleva.domain.fund;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface FundRepository extends CrudRepository<Fund, Long> {

    Fund findByName(String name);

    List<Fund> findByFundManager(FundManager fundManager);

    Fund findByIsin(String isin);


}
