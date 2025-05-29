package ee.tuleva.onboarding.swedbank.statement;

import ee.tuleva.onboarding.swedbank.statement.SwedbankStatementFetchJob.JobStatus;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
interface SwedbankStatementFetchJobRepository extends CrudRepository<SwedbankStatementFetchJob, UUID> {
  SwedbankStatementFetchJob findFirstByJobStatusOrderByCreatedAtDesc(JobStatus jobStatus);
}
