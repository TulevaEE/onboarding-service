package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.OK;

import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RebalanceAnnouncements {

  private static final String WINDOW_OPEN_ANNOUNCED = "MODEL_REBALANCE_WINDOW_OPEN_ANNOUNCED";
  private static final String WINDOW_CLOSE_ANNOUNCED = "MODEL_REBALANCE_WINDOW_CLOSE_ANNOUNCED";

  private final RebalanceAnnouncementLog announcementLog;

  boolean isOwed(RebalanceWindow window) {
    return switch (window.state()) {
      case WARNING, FAIL -> true;
      case INFO -> !closeOwed(window.fund(), window.modelEffectiveDate());
      case OK -> closeOwed(window.fund(), window.modelEffectiveDate());
    };
  }

  boolean closeOwed(TulevaFund fund, LocalDate modelEffectiveDate) {
    return latestAnnouncement(windowKey(fund, modelEffectiveDate))
        .filter(WINDOW_OPEN_ANNOUNCED::equals)
        .isPresent();
  }

  void recordAnnouncement(RebalanceWindow window, Instant announcedAt) {
    record(
        window, window.state() == OK ? WINDOW_CLOSE_ANNOUNCED : WINDOW_OPEN_ANNOUNCED, announcedAt);
  }

  private void record(RebalanceWindow window, String type, Instant at) {
    announcementLog.save(
        EventLog.builder()
            .type(type)
            .principal(windowKey(window.fund(), window.modelEffectiveDate()))
            .timestamp(at)
            .data(
                Map.of(
                    "state", window.state().name(),
                    "checkDate", window.checkDate().toString()))
            .build());
  }

  private Optional<String> latestAnnouncement(String windowKey) {
    return announcementLog
        .findFirstByPrincipalAndTypeInOrderByTimestampDescIdDesc(
            windowKey, List.of(WINDOW_OPEN_ANNOUNCED, WINDOW_CLOSE_ANNOUNCED))
        .map(EventLog::getType);
  }

  private static String windowKey(TulevaFund fund, LocalDate modelEffectiveDate) {
    return fund.getCode() + ":" + modelEffectiveDate;
  }
}
