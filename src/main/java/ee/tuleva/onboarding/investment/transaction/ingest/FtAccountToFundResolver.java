package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@NullMarked
@Component
class FtAccountToFundResolver {

  private static final Map<String, TulevaFund> BY_NORMALIZED_ACCOUNT =
      Map.of(
          normalize("Tuleva Additional Investment Fund"), TKF100,
          normalize("TULEVA III SAMBA PENSIONIFOND"), TUV100,
          normalize("MAAKPE"), TUK75);

  Optional<TulevaFund> resolve(String account) {
    return Optional.ofNullable(BY_NORMALIZED_ACCOUNT.get(normalize(account)));
  }

  private static String normalize(String account) {
    return account.trim().toLowerCase(Locale.ROOT);
  }
}
