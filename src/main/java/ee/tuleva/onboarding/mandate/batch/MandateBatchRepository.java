package ee.tuleva.onboarding.mandate.batch;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MandateBatchRepository extends CrudRepository<MandateBatch, Long> {

  @Transactional
  @Modifying
  @Query(
      "UPDATE MandateBatch batch SET batch.status ="
          + " ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.COMPLETED"
          + " WHERE batch.id = :id"
          + " AND batch.status = ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.SIGNED")
  int markSignedBatchCompleted(@Param("id") Long id);
}
