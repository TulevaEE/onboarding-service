package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import java.util.List;
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

  public void publish(NavCalculationResult result) {
    if (result.fund().isSavingsFund()) {
      publishNav(result);
      publishAum(result);
    }

    List<NavReportRow> reportRows = List.of();
    try {
      reportRows = navReportMapper.map(result);
      navReportRepository.saveAll(reportRows);
    } catch (Exception e) {
      log.error(
          "Failed to persist NAV report: fund={}, date={}",
          result.fund(),
          result.calculationDate(),
          e);
    }

    try {
      navReportEmailSender.send(reportRows, result);
    } catch (Exception e) {
      log.error(
          "Failed to send NAV report email: fund={}, date={}",
          result.fund(),
          result.calculationDate(),
          e);
    }

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
