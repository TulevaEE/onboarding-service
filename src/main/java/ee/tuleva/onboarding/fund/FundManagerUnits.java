package ee.tuleva.onboarding.fund;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class FundManagerUnits {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final SavingsFundUnitStats savingsFundUnitStats;
  private final FundManagerUnitsInRegister unitsInRegister;
  private final Clock clock;
  private final String fundManagerRegistryCode;

  FundManagerUnits(
      SavingsFundUnitStats savingsFundUnitStats,
      FundManagerUnitsInRegister unitsInRegister,
      Clock clock,
      @Value("${fund-manager.registry-code}") String fundManagerRegistryCode) {
    this.savingsFundUnitStats = savingsFundUnitStats;
    this.unitsInRegister = unitsInRegister;
    this.clock = clock;
    this.fundManagerRegistryCode = fundManagerRegistryCode;
  }

  List<ExtendedApiFundResponse> applyTo(List<ExtendedApiFundResponse> responses) {
    var lastMonthEnd = LocalDate.now(clock.withZone(ESTONIAN_ZONE)).withDayOfMonth(1).minusDays(1);
    responses.forEach(response -> applyTo(response, lastMonthEnd));
    return responses;
  }

  private void applyTo(ExtendedApiFundResponse response, LocalDate monthEnd) {
    TulevaFund.findByIsin(response.getIsin())
        .flatMap(fund -> heldAtOrNothing(fund, monthEnd))
        .ifPresent(
            units -> {
              response.setFundManagerUnits(units);
              response.setFundManagerUnitsDate(monthEnd);
            });
  }

  private Optional<BigDecimal> heldAtOrNothing(TulevaFund fund, LocalDate monthEnd) {
    try {
      return heldAt(fund, monthEnd);
    } catch (RuntimeException e) {
      log.error("Fund manager units not read: fund={}, monthEnd={}", fund, monthEnd, e);
      return Optional.empty();
    }
  }

  private Optional<BigDecimal> heldAt(TulevaFund fund, LocalDate monthEnd) {
    if (fund == TKF100) {
      var startOfNextMonth = monthEnd.plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant();
      return savingsFundUnitStats.unitsHeldAt(fundManagerRegistryCode, startOfNextMonth);
    }
    return unitsInRegister.fundManagerUnitsOn(fund.getIsin(), monthEnd);
  }
}
