package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
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

  public void publish(NavCalculationResult result) {
    if (result.fund().isSavingsFund()) {
      publishNav(result);
      publishAum(result);
    }

    UUID calculationId = UUID.randomUUID();
    List<NavReportRow> reportRows = List.of();
    try {
      reportRows = navReportMapper.map(result);
      reportRows.forEach(row -> row.setCalculationId(calculationId));
      navReportRepository.replaceByNavDateAndFundCode(
          result.positionReportDate(), result.fund().getCode(), reportRows);
    } catch (Exception e) {
      log.error(
          "Failed to persist NAV report: fund={}, calculationDate={}, positionReportDate={}",
          result.fund(),
          result.calculationDate(),
          result.positionReportDate(),
          e);
    }

    try {
      boolean emailSent = navReportEmailSender.send(reportRows, result);
      if (emailSent) {
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
    } catch (Exception e) {
      log.error(
          "Failed to send NAV report email: fund={}, date={}",
          result.fund(),
          result.calculationDate(),
          e);
      notificationService.sendMessage(
          "NAV report email failed: fund="
              + result.fund().getCode()
              + ", date="
              + result.positionReportDate(),
          SAVINGS);
    }

    // NAV/AUM values are always published to the API regardless of email success.
    // The email is the audit trail for trustees; the API serves members and internal systems.
    navNotifier.notify(result);

    log.info(
        "Published NAV: fund={}, date={}, navPerUnit={}, aum={}",
        result.fund(),
        result.calculationDate(),
        result.navPerUnit(),
        result.aum());
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
