package ee.tuleva.onboarding.investment.event.trigger;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface JobTriggerRepository extends JpaRepository<JobTrigger, Long> {

  List<JobTrigger> findByStatusOrderByCreatedAtAsc(String status);

  Optional<JobTrigger> findFirstByJobNameAndStatusIn(String jobName, Collection<String> statuses);
}
