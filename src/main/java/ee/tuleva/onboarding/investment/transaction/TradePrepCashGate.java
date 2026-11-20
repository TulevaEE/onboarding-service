package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.BankStatementBalances;
import ee.tuleva.onboarding.banking.FetchedBalance;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoSameDayCash;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.UnreadableSameDayCash;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
class TradePrepCashGate {

  private static final ZoneId BANK_DAY_ZONE = ZoneId.of("Europe/Tallinn");

  private final BankAccounts bankAccounts;
  private final BankStatementBalances bankStatementBalances;
  private final Clock clock;

  Optional<InputGateFailure> missingSameDayCash(TulevaFund fund, TradePrepOverrides overrides) {
    if (overrides.cash() != null) {
      return Optional.empty();
    }
    try {
      Optional<FetchedBalance> latest =
          bankAccounts.findAll(fund).stream()
              .filter(account -> account.matches(fund, FUND_INVESTMENT_EUR))
              .findFirst()
              .flatMap(bankStatementBalances::latestFetched);
      if (latest.filter(this::fetchedToday).isPresent()) {
        return Optional.empty();
      }
      return Optional.of(new NoSameDayCash(latest.map(FetchedBalance::fetchedAt).orElse(null)));
    } catch (RuntimeException e) {
      log.error("Same-day bank balance could not be read: fund={}", fund, e);
      return Optional.of(new UnreadableSameDayCash());
    }
  }

  private boolean fetchedToday(FetchedBalance balance) {
    LocalDate today = clock.instant().atZone(BANK_DAY_ZONE).toLocalDate();
    return balance.fetchedAt().atZone(BANK_DAY_ZONE).toLocalDate().equals(today);
  }
}
