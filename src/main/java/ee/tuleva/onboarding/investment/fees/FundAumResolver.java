package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.PositionCalculationRepository;
import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FundAumResolver {

  private static final List<AccountType> AUM_ACCOUNT_TYPES =
      List.of(CASH, SECURITY, RECEIVABLES, LIABILITY);

  private final PositionCalculationRepository positionCalculationRepository;
  private final FundPositionRepository fundPositionRepository;
  private final PublicHolidays publicHolidays;

  LocalDate resolveReferenceDate(TulevaFund fund, LocalDate calendarDate) {
    if (fund.hasNavCalculation()) {
      LocalDate referenceDate =
          fundPositionRepository
              .findLatestNavDateByFundAndAsOfDate(fund, calendarDate)
              .orElse(null);
      if (referenceDate != null
          && publicHolidays.isWorkingDay(calendarDate)
          && !referenceDate.equals(calendarDate)) {
        throw new IllegalStateException(
            "Position data missing: fund="
                + fund
                + ", expected="
                + calendarDate
                + ", latest="
                + referenceDate);
      }
      return referenceDate;
    }
    return positionCalculationRepository.getLatestDateUpTo(fund, calendarDate).orElse(null);
  }

  BigDecimal resolveBaseValue(TulevaFund fund, LocalDate referenceDate) {
    if (fund.hasNavCalculation()) {
      return fundPositionRepository.sumMarketValueByFundAndAccountTypes(
          fund, referenceDate, AUM_ACCOUNT_TYPES);
    }
    return positionCalculationRepository.getTotalMarketValue(fund, referenceDate).orElse(ZERO);
  }
}
