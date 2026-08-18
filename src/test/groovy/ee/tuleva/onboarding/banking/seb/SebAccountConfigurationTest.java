package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.BankAccountType.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SebAccountConfigurationTest {

  private static final String MANAGEMENT_COMPANY_NAME = "Tuleva Fondid AS";

  private final SebAccountConfiguration configuration =
      new SebAccountConfiguration(
          Map.of(DEPOSIT_EUR, "EE111111111111111111"), MANAGEMENT_COMPANY_NAME, List.of());

  @Test
  void isManagementCompany_matchesCaseInsensitively() {
    assertThat(configuration.isManagementCompany("Tuleva Fondid AS")).isTrue();
    assertThat(configuration.isManagementCompany("tuleva fondid as")).isTrue();
    assertThat(configuration.isManagementCompany("TULEVA FONDID AS")).isTrue();
  }

  @Test
  void isManagementCompany_returnsFalseForNonMatch() {
    assertThat(configuration.isManagementCompany("Some Other Company")).isFalse();
  }

  @Test
  void registrarIbans_defaultToEmptyWhenNotConfigured() {
    var withoutRegistrar =
        new SebAccountConfiguration(
            Map.of(DEPOSIT_EUR, "EE111111111111111111"), MANAGEMENT_COMPANY_NAME, null);

    assertThat(withoutRegistrar.getRegistrarIbans()).isEmpty();
  }
}
