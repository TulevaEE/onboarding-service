package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.epis.R16Phase.ACTIVE;
import static ee.tuleva.onboarding.investment.epis.R16Phase.BUFFERED;
import static ee.tuleva.onboarding.investment.epis.R16Phase.IGNORE;
import static ee.tuleva.onboarding.investment.epis.R16Phase.VISIBLE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class R16PhaseCalculator {

  private static final int R45_OVERLAP_WINDOW_START_DAY = 16;
  private static final int PAYMENT_WINDOW_END_DAY = 20;

  private final R45ReportService r45ReportService;

  public R16Phase phaseFor(@Nullable R16FundFlow flow, LocalDate today) {
    if (flow == null) {
      return IGNORE;
    }
    return phaseFor(flow.fund(), flow.paymentDates(), today);
  }

  public R16Phase phaseFor(TulevaFund fund, R16PaymentDates dates, LocalDate today) {
    if (today.isAfter(dates.paymentMonth().withDayOfMonth(PAYMENT_WINDOW_END_DAY))) {
      return IGNORE;
    }
    if (isSuppressedByR45(fund, dates.paymentMonth())) {
      return IGNORE;
    }
    if (today.isBefore(dates.sellByDate())) {
      return VISIBLE;
    }
    if (today.isBefore(dates.paymentDeadline())) {
      return ACTIVE;
    }
    return BUFFERED;
  }

  public boolean isSuppressedByR45(R16FundFlow flow) {
    return isSuppressedByR45(flow.fund(), flow.paymentMonth());
  }

  private boolean isSuppressedByR45(TulevaFund fund, LocalDate paymentMonth) {
    if (fund != TUV100) {
      return false;
    }
    LocalDate windowStart = paymentMonth.withDayOfMonth(R45_OVERLAP_WINDOW_START_DAY);
    LocalDate windowEnd = paymentMonth.withDayOfMonth(PAYMENT_WINDOW_END_DAY);
    return r45ReportService.getLatestRedRowSettlementDates(TUV100).stream()
        .anyMatch(date -> !date.isBefore(windowStart) && !date.isAfter(windowEnd));
  }
}
