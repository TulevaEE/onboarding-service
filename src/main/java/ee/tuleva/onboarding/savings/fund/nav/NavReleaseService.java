package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.savings.fund.nav.NavReleaseState.HELD;
import static ee.tuleva.onboarding.savings.fund.nav.NavReleaseState.NOT_FINAL;
import static ee.tuleva.onboarding.savings.fund.nav.NavReleaseState.REVISION;
import static ee.tuleva.onboarding.savings.fund.nav.NavReleaseState.SENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavReleaseService {

  private static final String NAV_ACCOUNT_TYPE = "NAV";
  private static final String UNITS_ACCOUNT_TYPE = "UNITS";

  private final NavReportRepository navReportRepository;
  private final NavReportEmailSender navReportEmailSender;
  private final NavCalendar navCalendar;
  private final OperationsNotificationService notificationService;

  @Transactional(readOnly = true)
  public NavReleaseStatus status(TulevaFund fund, LocalDate navDate) {
    UUID latest =
        navReportRepository
            .findLatestCalculationId(fund.getCode(), navDate)
            .orElseThrow(() -> noCalculation(fund, navDate));
    return statusOf(fund, navDate, navReportRepository.findByCalculationIdOrderById(latest));
  }

  @Transactional
  public NavReleaseStatus release(TulevaFund fund, LocalDate navDate, NavReleaseApproval approval) {
    List<NavReportRow> rows =
        navReportRepository.findCalculationRowsForUpdate(approval.calculationId());
    if (rows.isEmpty()) {
      throw new NoSuchElementException(
          "NAV calculation not found: calculationId=" + approval.calculationId());
    }
    NavReleaseStatus current = statusOf(fund, navDate, rows);
    refuseUnlessReleasable(current, approval);

    if (!navReportEmailSender.send(rows, fund, navDate, navCalendar.calculationDateOf(navDate))) {
      throw new NavReleaseEmailFailedException(
          "NAV report email to the trustee failed, nothing released: fund=%s, navDate=%s, calculationId=%s"
              .formatted(fund.getCode(), navDate, approval.calculationId()));
    }
    navReportRepository.markAsSent(approval.calculationId());
    announce(current, approval);
    return new NavReleaseStatus(
        current.fundCode(),
        navDate,
        current.calculationId(),
        SENT,
        current.navPerUnit(),
        current.aum(),
        current.navPerUnit());
  }

  private NavReleaseStatus statusOf(
      TulevaFund fund, LocalDate navDate, List<NavReportRow> calculationRows) {
    NavReportRow units = unitsRowOf(fund, navDate, calculationRows);
    return new NavReleaseStatus(
        fund.getCode(),
        navDate,
        units.getCalculationId(),
        stateOf(fund, navDate, units),
        units.getMarketPrice(),
        units.getMarketValue(),
        navReportRepository
            .findSentNavPerUnit(navDate, fund.getCode(), NAV_ACCOUNT_TYPE)
            .orElse(null));
  }

  private NavReleaseState stateOf(TulevaFund fund, LocalDate navDate, NavReportRow units) {
    if (units.getSentToTrusteeAt() != null) {
      return SENT;
    }
    if (units.getPublishedAt() == null) {
      return NOT_FINAL;
    }
    return navReportRepository.existsSentByNavDateAndFundCode(navDate, fund.getCode())
        ? REVISION
        : HELD;
  }

  private static NavReportRow unitsRowOf(
      TulevaFund fund, LocalDate navDate, List<NavReportRow> calculationRows) {
    return calculationRows.stream()
        .filter(row -> row.getFundCode().equals(fund.getCode()))
        .filter(row -> row.getNavDate().equals(navDate))
        .filter(row -> UNITS_ACCOUNT_TYPE.equals(row.getAccountType()))
        .findFirst()
        .orElseThrow(
            () ->
                new NavReleaseRefusedException(
                    "Calculation has no units row for this fund and date: fund=%s, navDate=%s, calculationId=%s"
                        .formatted(
                            fund.getCode(),
                            navDate,
                            calculationRows.getFirst().getCalculationId())));
  }

  private void refuseUnlessReleasable(NavReleaseStatus current, NavReleaseApproval approval) {
    if (!isTheDatesLatestCalculation(current)) {
      throw refusal("is not the date's latest calculation", current);
    }
    if (!current.state().isReleasable()) {
      throw refusal(
          "is %s, and only a HELD or REVISION calculation is released".formatted(current.state()),
          current);
    }
    if (!current.hasTheApprovedValues(approval)) {
      throw refusal(
          "differs from the approved values: approvedNavPerUnit=%s, approvedAum=%s"
              .formatted(approval.navPerUnit(), approval.aum()),
          current);
    }
  }

  private boolean isTheDatesLatestCalculation(NavReleaseStatus current) {
    return navReportRepository
        .findLatestCalculationId(current.fundCode(), current.navDate())
        .map(current.calculationId()::equals)
        .orElse(false);
  }

  private static NavReleaseRefusedException refusal(String reason, NavReleaseStatus current) {
    return new NavReleaseRefusedException(
        "NAV release refused, the calculation %s: fund=%s, navDate=%s, calculationId=%s, navPerUnit=%s, aum=%s"
            .formatted(
                reason,
                current.fundCode(),
                current.navDate(),
                current.calculationId(),
                current.navPerUnit(),
                current.aum()));
  }

  private void announce(NavReleaseStatus released, NavReleaseApproval approval) {
    String approvers = String.join(", ", approval.approvedBy());
    log.info(
        "NAV released to the trustee: fund={}, navDate={}, calculationId={}, state={}, navPerUnit={}, approvedBy={}",
        released.fundCode(),
        released.navDate(),
        released.calculationId(),
        released.state(),
        released.navPerUnit(),
        approvers);
    notificationService.sendMessage(
        "✅ NAV released and sent to SEB: fund=%s, navDate=%s, navPerUnit=%s, %s, approvedBy=%s"
            .formatted(
                released.fundCode(),
                released.navDate(),
                released.navPerUnit().toPlainString(),
                released.state() == REVISION
                    ? "replaces the NAV SEB had (" + released.sentNavPerUnit() + ")"
                    : "held for review until now",
                approvers),
        SAVINGS);
  }

  private static NoSuchElementException noCalculation(TulevaFund fund, LocalDate navDate) {
    return new NoSuchElementException(
        "No NAV calculation for this date: fund=%s, navDate=%s".formatted(fund.getCode(), navDate));
  }
}
