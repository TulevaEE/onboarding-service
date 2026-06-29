package ee.tuleva.onboarding.event;

import org.springframework.data.repository.CrudRepository;

public interface EventLogRepository extends CrudRepository<EventLog, Long> {

  boolean existsByTypeAndPrincipal(String type, String principal);
}
