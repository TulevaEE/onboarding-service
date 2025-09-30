package ee.tuleva.onboarding.swedbank.fetcher;

import ee.tuleva.onboarding.swedbank.fetcher.SwedbankStatementFetchJob.JobStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
interface SwedbankStatementFetchJobRepository
    extends CrudRepository<SwedbankStatementFetchJob, UUID> {
  Optional<SwedbankStatementFetchJob> findFirstByJobStatusOrderByCreatedAtDesc(JobStatus jobStatus);

  Optional<SwedbankStatementFetchJob> findFirstByJobStatusAndIbanEqualsOrderByCreatedAtDesc(
      JobStatus jobStatus, String iban);
}
