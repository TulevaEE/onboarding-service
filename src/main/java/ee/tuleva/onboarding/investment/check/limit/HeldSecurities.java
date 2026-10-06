package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class HeldSecurities {

  private final FundPositionRepository fundPositionRepository;
  private final NavReportPositionProvider navReportPositionProvider;

  Optional<LocalDate> lastPositionDateOnOrBefore(TulevaFund fund, LocalDate date) {
    return fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, date);
  }

  List<HeldSecurity> on(TulevaFund fund, LocalDate positionDate) {
    var navMarketValues = navReportPositionProvider.getSecurityMarketValues(fund, positionDate);
    return fundPositionRepository
        .findByNavDateAndFundAndAccountType(positionDate, fund, SECURITY)
        .stream()
        .map(HeldSecurity::of)
        .collect(toMap(HeldSecurity::key, identity(), HeldSecurity::plus, LinkedHashMap::new))
        .values()
        .stream()
        .filter(held -> !held.isSoldOut())
        .map(held -> held.valuedByTheNavReportWhereItHasTheIsin(navMarketValues))
        .toList();
  }

  record HeldSecurity(@Nullable String isin, String name, @Nullable BigDecimal value) {

    static HeldSecurity of(FundPosition position) {
      return new HeldSecurity(
          position.getAccountId(), position.getAccountName(), position.getMarketValue());
    }

    String key() {
      return isin != null ? isin : name;
    }

    HeldSecurity valuedByTheNavReportWhereItHasTheIsin(Map<String, BigDecimal> navMarketValues) {
      return isin != null && navMarketValues.containsKey(isin)
          ? new HeldSecurity(isin, name, navMarketValues.get(isin))
          : this;
    }

    HeldSecurity plus(HeldSecurity other) {
      var sum = value == null || other.value == null ? null : value.add(other.value);
      return new HeldSecurity(isin, name, sum);
    }

    boolean isSoldOut() {
      return value != null && value.signum() <= 0;
    }
  }
}
