package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.epis.SummaryData.number;
import static ee.tuleva.onboarding.investment.report.ReportType.R16_FORECASTED_PAYMENTS;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class R16FlowCalculationService {

  private static final int PAYMENT_DEADLINE_DAY_OF_MONTH = 15;
  private static final int SELL_BY_BUSINESS_DAYS_BEFORE_DEADLINE = 5;

  private final EpisReportSummaryRepository summaryRepository;
  private final OwnFundNavProvider ownFundNavProvider;
  private final EstonianCalendar estonianCalendar;

  public Optional<R16FundFlow> calculateFlows(TulevaFund fund, LocalDate asOfDate) {
    return summaryRepository
        .findTopByReportTypeAndFundOrderByReportDateDescIdDesc(R16_FORECASTED_PAYMENTS, fund)
        .map(summary -> toFlow(fund, summary, asOfDate));
  }

  private R16FundFlow toFlow(TulevaFund fund, EpisReportSummary summary, LocalDate asOfDate) {
    BigDecimal fondimaksedUnits = number(summary.getData(), "fondimaksedUnits");
    BigDecimal uhekordsedUnits = number(summary.getData(), "uhekordsedUnits");
    YearMonth paymentMonth = YearMonth.parse(String.valueOf(summary.getData().get("paymentMonth")));

    BigDecimal nav = ownFundNavProvider.latestNav(fund, asOfDate);
    BigDecimal totalOutflowEur = fondimaksedUnits.add(uhekordsedUnits).multiply(nav);

    LocalDate paymentDeadline =
        estonianCalendar.nextOrSameBusinessDay(paymentMonth.atDay(PAYMENT_DEADLINE_DAY_OF_MONTH));
    LocalDate sellByDate =
        estonianCalendar.subtractBusinessDays(
            paymentDeadline, SELL_BY_BUSINESS_DAYS_BEFORE_DEADLINE);

    return new R16FundFlow(
        fund,
        fondimaksedUnits,
        uhekordsedUnits,
        totalOutflowEur,
        paymentMonth.atDay(1),
        paymentDeadline,
        sellByDate);
  }
}
