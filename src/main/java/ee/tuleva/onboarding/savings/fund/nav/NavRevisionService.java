package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class NavRevisionService {

  private final NavReportRepository navReportRepository;
  private final FundNavQueryService fundNavQueryService;
  private final NavCalculationService navCalculationService;
  private final NavPublisher navPublisher;
  private final OperationsNotificationService notificationService;
  private final PublicHolidays publicHolidays;

  @EventListener
  void onNavPositionsUpdated(NavPositionsUpdated event) {
    String fundCode = event.fund().getCode();
    if (!navReportRepository.existsPublishedByNavDateAndFundCode(event.navDate(), fundCode)) {
      log.info(
          "Positions changed before NAV publication, no revision needed: fund={}, navDate={}, changedRows={}",
          fundCode,
          event.navDate(),
          event.changedRows());
      return;
    }
    try {
      revise(event);
    } catch (Exception e) {
      log.error(
          "NAV revision failed after position change: fund={}, navDate={}, changedRows={}",
          fundCode,
          event.navDate(),
          event.changedRows(),
          e);
      notificationService.sendMessage(revisionFailedMessage(event), SAVINGS);
    }
  }

  private void revise(NavPositionsUpdated event) {
    String fundCode = event.fund().getCode();
    BigDecimal publishedNav =
        fundNavQueryService
            .findPublishedNavPerUnit(fundCode, event.navDate())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Published NAV row missing: fund=%s, navDate=%s"
                            .formatted(fundCode, event.navDate())));
    NavCalculationResult revised =
        navCalculationService.calculate(
            event.fund(), publicHolidays.nextWorkingDay(event.navDate()));
    navPublisher.publishRevision(revised, new NavRevision(publishedNav, event.changedRows()));
  }

  private String revisionFailedMessage(NavPositionsUpdated event) {
    return """
        🔴 NAV revision FAILED after the custodian position report changed: fund=%s, navDate=%s, changedRows=%d
        The published NAV for that date may be stale. Recalculate manually:
        POST /admin/calculate-nav?fundCode=%s&date=<next working day>&publish=false"""
        .formatted(
            event.fund().getCode(), event.navDate(), event.changedRows(), event.fund().getCode());
  }
}
