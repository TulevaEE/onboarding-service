package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.OK;

import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.time.LocalDate;
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
    return window.state() != OK || closeOwed(window.fund(), window.modelEffectiveDate());
  }

  boolean closeOwed(TulevaFund fund, LocalDate modelEffectiveDate) {
    var key = windowKey(fund, modelEffectiveDate);
    return eventLogRepository.existsByTypeAndPrincipal(WINDOW_OPEN_ANNOUNCED, key)
        && !eventLogRepository.existsByTypeAndPrincipal(WINDOW_CLOSE_ANNOUNCED, key);
  }

  void recordAnnounced(RebalanceWindow window, Instant announcedAt) {
    eventLogRepository.save(
        EventLog.builder()
            .type(window.state() == OK ? WINDOW_CLOSE_ANNOUNCED : WINDOW_OPEN_ANNOUNCED)
            .principal(windowKey(window.fund(), window.modelEffectiveDate()))
            .timestamp(announcedAt)
            .data(
                Map.of(
                    "state", window.state().name(),
                    "checkDate", window.checkDate().toString()))
            .build());
  }

  private static String windowKey(TulevaFund fund, LocalDate modelEffectiveDate) {
    return fund.getCode() + ":" + modelEffectiveDate;
  }
}
