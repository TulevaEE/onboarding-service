package ee.tuleva.onboarding.investment.check.rebalance;

import ee.tuleva.onboarding.event.EventLog;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.repository.Repository;

interface RebalanceAnnouncementLog extends Repository<EventLog, Long> {

  EventLog save(EventLog announcement);

  Optional<EventLog> findFirstByPrincipalAndTypeInOrderByTimestampDescIdDesc(
      String principal, Collection<String> types);
}
