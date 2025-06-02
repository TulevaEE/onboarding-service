package ee.tuleva.onboarding.swedbank.statement;

import ee.tuleva.onboarding.swedbank.statement.SwedbankStatementFetchJob.JobStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
interface SwedbankStatementFetchJobRepository
    extends CrudRepository<SwedbankStatementFetchJob, UUID> {
  Optional<SwedbankStatementFetchJob> findFirstByJobStatusOrderByCreatedAtDesc(JobStatus jobStatus);
}
