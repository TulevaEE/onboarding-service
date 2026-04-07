package ee.tuleva.onboarding.investment.event.trigger;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface JobTriggerRepository extends JpaRepository<JobTrigger, Long> {

  List<JobTrigger> findByStatusOrderByCreatedAtAsc(String status);
}
