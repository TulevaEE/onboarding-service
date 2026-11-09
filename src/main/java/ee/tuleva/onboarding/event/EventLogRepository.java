package ee.tuleva.onboarding.event;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.CrudRepository;

public interface EventLogRepository extends CrudRepository<EventLog, Long> {

  boolean existsByTypeAndPrincipal(String type, String principal);

  boolean existsByTypeAndPrincipalAndTimestampGreaterThanEqual(
      String type, String principal, Instant since);

  List<EventLog> findAllByTypeInAndTimestampGreaterThanEqualOrderByTimestamp(
      Collection<String> types, Instant since);

  List<EventLog> findAllByTypeInAndTimestampLessThan(Collection<String> types, Instant before);

  @Lock(PESSIMISTIC_WRITE)
  Optional<EventLog> findFirstByTypeAndPrincipalOrderByTimestampDesc(String type, String principal);

  @Lock(PESSIMISTIC_WRITE)
  Optional<EventLog> findLockedById(Long id);
}
