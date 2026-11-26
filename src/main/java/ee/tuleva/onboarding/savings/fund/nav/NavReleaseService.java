package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.savings.fund.nav.NavReleaseState.HELD;
import static ee.tuleva.onboarding.savings.fund.nav.NavReleaseState.REVISION;
import static ee.tuleva.onboarding.savings.fund.nav.NavReleaseState.SENT;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.NavCalculationCompleted;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
  private final TransactionTemplate transactionTemplate;
  private final PublicHolidays publicHolidays;
  private final Clock clock;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public NavReleaseStatus status(TulevaFund fund, LocalDate navDate) {
    UUID latest =
        navReportRepository
            .findLatestCalculationId(fund.getCode(), navDate)
            .orElseThrow(() -> noCalculation(fund, navDate));
    return statusOf(fund, navDate, navReportRepository.findByCalculationIdOrderById(latest));
  }

  public NavReleaseStatus release(TulevaFund fund, LocalDate navDate, NavReleaseApproval approval) {
    NavReleaseStatus released =
        requireNonNull(
            transactionTemplate.execute(transaction -> sendAndStamp(fund, navDate, approval)));
    announce(released, approval);
    rerunTheChecksOfTheReleasedNav(fund, navDate);
    return new NavReleaseStatus(
        released.fundCode(),
        navDate,
        released.calculationId(),
        SENT,
        released.navPerUnit(),
        released.aum(),
        released.navPerUnit());
  }

  private NavReleaseStatus sendAndStamp(
      TulevaFund fund, LocalDate navDate, NavReleaseApproval approval) {
    List<NavReportRow> rows =
        latestCalculationOf(navReportRepository.findTheDatesRowsForUpdate(fund.getCode(), navDate))
            .orElseThrow(() -> noCalculation(fund, navDate));
    NavReleaseStatus current = statusOf(fund, navDate, rows);
    refuseUnlessReleasable(current, approval);

    if (!sendToTheTrustee(rows, fund, navDate)) {
      throw new NavReleaseEmailFailedException(
          "NAV report email to the trustee failed, nothing released: fund=%s, navDate=%s, calculationId=%s"
              .formatted(fund.getCode(), navDate, current.calculationId()));
    }
    navReportRepository.markAsPublished(current.calculationId());
    return current;
  }

  private static Optional<List<NavReportRow>> latestCalculationOf(List<NavReportRow> theDatesRows) {
    if (theDatesRows.isEmpty()) {
      return Optional.empty();
    }
    UUID latest = theDatesRows.getLast().getCalculationId();
    return Optional.of(
        theDatesRows.stream().filter(row -> latest.equals(row.getCalculationId())).toList());
  }

  private boolean sendToTheTrustee(List<NavReportRow> rows, TulevaFund fund, LocalDate navDate) {
    try {
      return navReportEmailSender.send(rows, fund, navDate, navCalendar.calculationDateOf(navDate));
    } catch (RuntimeException e) {
      log.error(
          "NAV report email to the trustee threw: fund={}, navDate={}", fund.getCode(), navDate, e);
      return false;
    }
  }

  private void rerunTheChecksOfTheReleasedNav(TulevaFund fund, LocalDate navDate) {
    if (!navDate.equals(publicHolidays.previousWorkingDay(LocalDate.now(clock)))) {
      log.info(
          "NAV released after its own NAV day, its checks are not rerun: fund={}, navDate={}",
          fund.getCode(),
          navDate);
      return;
    }
    try {
      eventPublisher.publishEvent(new NavCalculationCompleted(List.of(fund)));
    } catch (RuntimeException e) {
      log.error("Checks after a NAV release failed: fund={}", fund.getCode(), e);
    }
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
            .findPublishedNavPerUnit(navDate, fund.getCode(), NAV_ACCOUNT_TYPE)
            .orElse(null));
  }

  private NavReleaseState stateOf(TulevaFund fund, LocalDate navDate, NavReportRow units) {
    if (units.getPublishedAt() != null) {
      return SENT;
    }
    return navReportRepository.existsPublishedByNavDateAndFundCode(navDate, fund.getCode())
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

  private static NavReleaseRefusedException refusal(String reason, NavReleaseStatus current) {
    return new NavReleaseRefusedException(
        "NAV release refused, the date's latest calculation %s: fund=%s, navDate=%s, calculationId=%s, navPerUnit=%s, aum=%s"
            .formatted(
                reason,
                current.fundCode(),
                current.navDate(),
                current.calculationId(),
                current.navPerUnit(),
                current.aum()));
  }

  private void announce(NavReleaseStatus released, NavReleaseApproval approval) {
    try {
      postReleaseMessage(released, approval);
    } catch (RuntimeException e) {
      log.error(
          "NAV release message failed, the release itself stands: fund={}, navDate={}, calculationId={}",
          released.fundCode(),
          released.navDate(),
          released.calculationId(),
          e);
    }
  }

  private void postReleaseMessage(NavReleaseStatus released, NavReleaseApproval approval) {
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
