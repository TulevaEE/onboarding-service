package ee.tuleva.onboarding.populationregister;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface PopulationRegisterResponseRepository
    extends CrudRepository<PopulationRegisterResponse, Long> {

  @Query(
      """
      select r from PopulationRegisterResponse r
      where r.personalCode = :personalCode
        and r.queryType = :queryType
        and r.response is not null
        and r.createdAt >= :notOlderThan
      order by r.createdAt desc
      limit 1
      """)
  Optional<PopulationRegisterResponse> findLatestSince(
      @Param("personalCode") String personalCode,
      @Param("queryType") PopulationRegisterQueryType queryType,
      @Param("notOlderThan") Instant notOlderThan);

  @Modifying
  @Query(
      """
      update PopulationRegisterResponse r
      set r.response = null
      where r.createdAt < :cutoff and r.response is not null
      """)
  int eraseResponsesOlderThan(@Param("cutoff") Instant cutoff);
}
