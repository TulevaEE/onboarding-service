package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
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
  private final NavCalendar navCalendar;

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
    Optional<BigDecimal> navTheTrusteeHas =
        fundNavQueryService.findPublishedNavPerUnit(event.fund().getCode(), event.navDate());
    NavCalculationResult recalculated =
        navCalculationService.calculate(event.fund(), calculationDate(event));
    navTheTrusteeHas.ifPresentOrElse(
        sentNav ->
            navPublisher.publishRevision(
                recalculated, new NavRevision(sentNav, event.changedRows())),
        () -> recalculateHeldNav(event, recalculated));
  }

  private void recalculateHeldNav(NavPositionsUpdated event, NavCalculationResult recalculated) {
    String fundCode = event.fund().getCode();
    if (event.fund().isSavingsFund()) {
      reviseTheNavTheWebsiteShows(event, recalculated);
      return;
    }
    log.info(
        "Positions changed while the date's NAV is held for review, recalculating it as a new NAV: fund={}, navDate={}, changedRows={}",
        fundCode,
        event.navDate(),
        event.changedRows());
    navPublisher.publish(recalculated);
  }

  private void reviseTheNavTheWebsiteShows(
      NavPositionsUpdated event, NavCalculationResult recalculated) {
    String fundCode = event.fund().getCode();
    BigDecimal heldNav =
        fundNavQueryService
            .findLatestNavPerUnit(fundCode, event.navDate())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Held NAV row missing: fund=%s, navDate=%s"
                            .formatted(fundCode, event.navDate())));
    navPublisher.publishRevision(recalculated, new NavRevision(heldNav, event.changedRows()));
  }

  private LocalDate calculationDate(NavPositionsUpdated event) {
    return navCalendar.calculationDateOf(event.navDate());
  }

  private String revisionFailedMessage(NavPositionsUpdated event) {
    String fundCode = event.fund().getCode();
    return """
        🔴 NAV revision FAILED after the custodian position report changed: fund=%s, navDate=%s, changedRows=%d
        The published NAV for that date may be stale. Recalculate manually:
        POST /admin/calculate-nav?fundCode=%s&date=%s&publish=false"""
        .formatted(
            fundCode, event.navDate(), event.changedRows(), fundCode, calculationDate(event));
  }
}
