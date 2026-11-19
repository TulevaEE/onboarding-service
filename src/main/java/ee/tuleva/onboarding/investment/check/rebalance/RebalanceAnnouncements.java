package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.OK;

import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RebalanceAnnouncements {

  private static final String WINDOW_OPEN_ANNOUNCED = "MODEL_REBALANCE_WINDOW_OPEN_ANNOUNCED";
  private static final String WINDOW_CLOSE_ANNOUNCED = "MODEL_REBALANCE_WINDOW_CLOSE_ANNOUNCED";

  private final EventLogRepository eventLogRepository;

  boolean isOwed(RebalanceWindow window) {
    return window.state() != OK || closeOfAnAnnouncedWindowIsOwed(window);
  }

  void recordAnnounced(RebalanceWindow window, Instant announcedAt) {
    eventLogRepository.save(
        EventLog.builder()
            .type(window.state() == OK ? WINDOW_CLOSE_ANNOUNCED : WINDOW_OPEN_ANNOUNCED)
            .principal(windowKey(window))
            .timestamp(announcedAt)
            .data(
                Map.of(
                    "state", window.state().name(),
                    "checkDate", window.checkDate().toString()))
            .build());
  }

  private boolean closeOfAnAnnouncedWindowIsOwed(RebalanceWindow window) {
    var key = windowKey(window);
    return eventLogRepository.existsByTypeAndPrincipal(WINDOW_OPEN_ANNOUNCED, key)
        && !eventLogRepository.existsByTypeAndPrincipal(WINDOW_CLOSE_ANNOUNCED, key);
  }

  private static String windowKey(RebalanceWindow window) {
    return window.fund().getCode() + ":" + window.modelEffectiveDate();
  }
}
