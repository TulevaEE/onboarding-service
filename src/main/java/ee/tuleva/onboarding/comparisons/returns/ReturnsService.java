package ee.tuleva.onboarding.comparisons.returns;

import static ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider.THIRD_PILLAR;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider;
import ee.tuleva.onboarding.comparisons.returns.Returns.Return;
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReturnsService {

  private final List<ReturnProvider> returnProviders;
  private final AccountOverviewProvider accountOverviewProvider;

  public Returns get(Person person, LocalDate fromDate, List<String> keys) {
    int pillar = getPillar(keys);
    Instant fromTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC);

    if (pillar == 3) {
      if (!wasThereABalanceAYearAgo(person, pillar)) {
        return Returns.builder().from(fromDate).notEnoughHistory(true).build();
      }
    }

    List<Return> returns =
        returnProviders.stream()
            .filter(
                returnProvider ->
                    keys == null || !Collections.disjoint(keys, returnProvider.getKeys()))
            .map(returnProvider -> returnProvider.getReturns(person, fromTime, pillar).getReturns())
            .flatMap(List::stream)
            .filter(aReturn -> keys == null || keys.contains(aReturn.getKey()))
            .toList();

    return Returns.builder().from(fromDate).returns(returns).notEnoughHistory(false).build();
  }

  private boolean wasThereABalanceAYearAgo(Person person, int pillar) {
    AccountOverview accountOverview =
        accountOverviewProvider.getAccountOverview(person, aYearAgo().truncatedTo(DAYS), pillar);
    return accountOverview.getBeginningBalance().compareTo(BigDecimal.ZERO) > 0;
  }

  private Integer getPillar(List<String> keys) {
    if (keys != null && keys.contains(THIRD_PILLAR)) {
      return 3;
    } else {
      return 2;
    }
  }
}
