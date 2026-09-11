package ee.tuleva.onboarding.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import org.junit.jupiter.api.Test;

class ManagementCompaniesTest {

  @Test
  void isManagementCompany_delegatesToSebAccountConfiguration() {
    var configuration = mock(SebAccountConfiguration.class);
    given(configuration.isManagementCompany("Tuleva Fondid AS")).willReturn(true);
    given(configuration.isManagementCompany("Other OÜ")).willReturn(false);
    var managementCompanies = new ManagementCompanies(configuration);

    assertThat(managementCompanies.isManagementCompany("Tuleva Fondid AS")).isTrue();
    assertThat(managementCompanies.isManagementCompany("Other OÜ")).isFalse();
  }
}
