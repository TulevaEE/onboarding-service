package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.investment.check.tracking.NavTrackingDifferenceGate;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavPublisher {

  private static final String NAV_PROVIDER = "TULEVA";

  private final FundValueRepository fundValueRepository;
  private final NavReportMapper navReportMapper;
  private final NavReportRepository navReportRepository;
  private final NavReportEmailSender navReportEmailSender;
  private final NavNotifier navNotifier;
  private final OperationsNotificationService notificationService;
  private final NavTrackingDifferenceGate trackingDifferenceGate;

  // NAV/AUM go to the FundValue API (and navNotifier) regardless of trustee email outcome.
  // The trustee email is the audit trail; the API serves members and internal systems.
  public void publish(NavCalculationResult result) {
    publishToFundValueApi(result);

    UUID calculationId = UUID.randomUUID();
    List<NavReportRow> reportRows = persistReportRows(result, calculationId);

    if (reportRows.isEmpty()) {
      notifyEmptyReport(result);
    } else {
      sendReportEmailWithGate(result, reportRows, calculationId);
    }

    navNotifier.notify(result);

    log.info(
        "Published NAV: fund={}, date={}, navPerUnit={}, aum={}",
        result.fund(),
        result.calculationDate(),
        result.navPerUnit(),
        result.aum());
  }

  private void publishToFundValueApi(NavCalculationResult result) {
    if (result.fund().isSavingsFund()) {
      publishNav(result);
      publishAum(result);
    }
  }

  private List<NavReportRow> persistReportRows(NavCalculationResult result, UUID calculationId) {
    try {
      var rows = navReportMapper.map(result);
      rows.forEach(row -> row.setCalculationId(calculationId));
      navReportRepository.replaceByNavDateAndFundCode(
          result.positionReportDate(), result.fund().getCode(), rows);
      return rows;
    } catch (Exception e) {
      log.error(
          "Failed to persist NAV report: fund={}, calculationDate={}, positionReportDate={}",
          result.fund(),
          result.calculationDate(),
          result.positionReportDate(),
          e);
      return List.of();
    }
  }

  private void notifyEmptyReport(NavCalculationResult result) {
    log.error(
        "No report rows to publish, skipping email: fund={}, date={}",
        result.fund(),
        result.positionReportDate());
    notificationService.sendMessage(
        "NAV report has no rows: fund="
            + result.fund().getCode()
            + ", date="
            + result.positionReportDate(),
        SAVINGS);
  }

  private void sendReportEmailWithGate(
      NavCalculationResult result, List<NavReportRow> reportRows, UUID calculationId) {
    Optional<String> gateFailure = checkGates(result);
    if (gateFailure.isPresent()) {
      log.error(
          "NAV report blocked by gate, rows remain unpublished: fund={}, date={}, reason={}",
          result.fund(),
          result.positionReportDate(),
          gateFailure.get());
      notificationService.sendMessage("NAV report blocked: " + gateFailure.get(), SAVINGS);
      return;
    }

    if (sendEmail(reportRows, result)) {
      navReportRepository.markAsPublished(calculationId);
    } else {
      log.error(
          "NAV report email failed, rows remain unpublished: fund={}, date={}",
          result.fund(),
          result.positionReportDate());
      notificationService.sendMessage(
          "NAV report email failed: fund="
              + result.fund().getCode()
              + ", date="
              + result.positionReportDate(),
          SAVINGS);
    }
  }

  private boolean sendEmail(List<NavReportRow> reportRows, NavCalculationResult result) {
    try {
      return navReportEmailSender.send(reportRows, result);
    } catch (Exception e) {
      log.error(
          "Failed to send NAV report email: fund={}, calculationDate={}, positionReportDate={}",
          result.fund(),
          result.calculationDate(),
          result.positionReportDate(),
          e);
      return false;
    }
  }

  private Optional<String> checkGates(NavCalculationResult result) {
    try {
      var tdResult = trackingDifferenceGate.check(result.fund(), result.positionReportDate());
      if (tdResult.isPresent()) {
        return tdResult;
      }
    } catch (Exception e) {
      log.warn(
          "TD gate error, proceeding with email: fund={}, date={}",
          result.fund(),
          result.positionReportDate(),
          e);
    }
    return Optional.empty();
  }

  private void publishNav(NavCalculationResult result) {
    FundValue navValue =
        new FundValue(
            result.fund().getIsin(),
            result.positionReportDate(),
            result.navPerUnit(),
            NAV_PROVIDER,
            result.calculatedAt());

    fundValueRepository.save(navValue);
  }

  private void publishAum(NavCalculationResult result) {
    FundValue aumValue =
        new FundValue(
            result.fund().getAumKey(),
            result.positionReportDate(),
            result.aum(),
            NAV_PROVIDER,
            result.calculatedAt());

    fundValueRepository.save(aumValue);
  }
}
