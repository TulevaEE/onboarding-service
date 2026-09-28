package ee.tuleva.onboarding.investment.cashbuffer;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.time.YearMonth;

record CashBufferReview(
    TulevaFund fund,
    YearMonth reviewMonth,
    LocalDate reviewedOn,
    FlowWindow window,
    Recommendation recommendation,
    ConfiguredReserve configured,
    Drift drift) {}
