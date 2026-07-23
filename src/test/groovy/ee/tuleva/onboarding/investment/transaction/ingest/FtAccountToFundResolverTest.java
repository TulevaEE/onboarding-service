package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FtAccountToFundResolverTest {

  private final FtAccountToFundResolver resolver = new FtAccountToFundResolver();

  @Test
  void resolvesTkf100Alias() {
    Optional<TulevaFund> result = resolver.resolve("Tuleva Additional Investment Fund");

    assertThat(result).contains(TKF100);
  }

  @Test
  void resolvesTuv100Alias() {
    Optional<TulevaFund> result = resolver.resolve("TULEVA III SAMBA PENSIONIFOND");

    assertThat(result).contains(TUV100);
  }

  @Test
  void resolvesTuk75Alias() {
    Optional<TulevaFund> result = resolver.resolve("MAAKPE");

    assertThat(result).contains(TUK75);
  }

  @Test
  void resolvesAliasCaseInsensitiveAndTrimmed() {
    Optional<TulevaFund> result = resolver.resolve("  maakpe  ");

    assertThat(result).contains(TUK75);
  }

  @Test
  void returnsEmptyForUnrecognizedAccount() {
    Optional<TulevaFund> result = resolver.resolve("Some Unknown Fund");

    assertThat(result).isEmpty();
  }
}
