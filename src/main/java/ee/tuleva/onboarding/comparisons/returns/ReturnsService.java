package ee.tuleva.onboarding.comparisons.returns;

import static ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider.THIRD_PILLAR;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.AccountOverviewProvider;
import ee.tuleva.onboarding.comparisons.returns.Returns.Return;
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnProvider;
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
      if (!isAnyTransactionsBeforeAYear(person, pillar, fromTime)) {
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
            .collect(toList());

    return Returns.builder().from(fromDate).returns(returns).notEnoughHistory(false).build();
  }

  private boolean isAnyTransactionsBeforeAYear(Person person, int pillar, Instant fromTime) {
    AccountOverview accountOverview =
        accountOverviewProvider.getAccountOverview(person, fromTime, pillar);
    return accountOverview.getTransactions().stream()
            .filter(transaction -> transaction.time().isBefore(aYearAgo()))
            .count()
        > 0;
  }

  private Integer getPillar(List<String> keys) {
    if (keys != null && keys.contains(THIRD_PILLAR)) {
      return 3;
    } else {
      return 2;
    }
  }
}
