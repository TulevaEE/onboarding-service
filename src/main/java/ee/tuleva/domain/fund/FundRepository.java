package ee.tuleva.domain.fund;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FundRepository extends CrudRepository<Fund, Long> {

    Fund findByName(String name);

    @Query("SELECT f FROM Fund f WHERE LOWER(name) = LOWER(:name)")
    Fund findByNameIgnoreCase(@Param("name") String name);

    List<Fund> findByFundManager(FundManager fundManager);

    Fund findByIsin(String isin);

}
