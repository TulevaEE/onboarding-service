package ee.tuleva.onboarding.fund;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FundRepository extends CrudRepository<Fund, Long> {

    @NotNull
    @Override
    @Query("select f from Fund f where f.status = 'ACTIVE'")
    Iterable<Fund> findAll();

    @Query("select f from Fund f where lower(f.fundManager.name) = lower(:fundManagerName) and f.status = 'ACTIVE'")
    Iterable<Fund> findAllByFundManagerNameIgnoreCase(@Param("fundManagerName") String fundManagerName);

    Fund findByIsin(String isin);

    @Query("select f from Fund f where f.pillar = :pillar and f.status = 'ACTIVE'")
    List<Fund> findAllByPillar(@Param("pillar") Integer pillar);
}
