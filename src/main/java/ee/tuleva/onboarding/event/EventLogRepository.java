package ee.tuleva.onboarding.event;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface EventLogRepository extends CrudRepository<EventLog, Long> {

  boolean existsByTypeAndPrincipal(String type, String principal);

  boolean existsByTypeAndPrincipalAndTimestampGreaterThanEqual(
      String type, String principal, Instant since);

  List<EventLog> findAllByTypeIn(Collection<String> types);
}
