package ee.tuleva.onboarding.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ManagementCompaniesTest {

  private static final String MANAGEMENT_COMPANY = "Tuleva Fondid AS";

  private final SebAccountConfiguration configuration = mock(SebAccountConfiguration.class);
  private final ManagementCompanies managementCompanies = new ManagementCompanies(configuration);

  @Test
  void isManagementCompany_delegatesToSebAccountConfiguration() {
    given(configuration.isManagementCompany(MANAGEMENT_COMPANY)).willReturn(true);
    given(configuration.isManagementCompany("Other OÜ")).willReturn(false);

    assertThat(managementCompanies.isManagementCompany(MANAGEMENT_COMPANY)).isTrue();
    assertThat(managementCompanies.isManagementCompany("Other OÜ")).isFalse();
  }

  @Test
  void aDebitToTheManagementCompanyDescribedAsAFeeIsAManagementFee() {
    given(configuration.isManagementCompany(MANAGEMENT_COMPANY)).willReturn(true);

    assertThat(managementCompanies.isManagementFee(feeDebit())).isTrue();
  }

  @Test
  void aFeeDescriptionIsRecognisedWhateverItsCase() {
    given(configuration.isManagementCompany(MANAGEMENT_COMPANY)).willReturn(true);

    assertThat(
            managementCompanies.isManagementFee(
                debit("-742.34", MANAGEMENT_COMPANY, "VALITSEMISTASU 02/26")))
        .isTrue();
  }

  @Test
  void aDebitToSomeoneElseIsNotAManagementFee() {
    given(configuration.isManagementCompany("Impostor OÜ")).willReturn(false);

    assertThat(
            managementCompanies.isManagementFee(
                debit("-742.34", "Impostor OÜ", "Valitsemistasu 02/26")))
        .isFalse();
  }

  @Test
  void aDebitDescribedAsSomethingElseIsNotAManagementFee() {
    given(configuration.isManagementCompany(MANAGEMENT_COMPANY)).willReturn(true);

    assertThat(
            managementCompanies.isManagementFee(
                debit("-742.34", MANAGEMENT_COMPANY, "Depootasu 02/26")))
        .isFalse();
  }

  @Test
  void aDebitWithNoDescriptionIsNotAManagementFee() {
    given(configuration.isManagementCompany(MANAGEMENT_COMPANY)).willReturn(true);

    assertThat(managementCompanies.isManagementFee(debit("-742.34", MANAGEMENT_COMPANY, null)))
        .isFalse();
  }

  @Test
  void moneyComingBackFromTheManagementCompanyIsNotAManagementFee() {
    assertThat(
            managementCompanies.isManagementFee(
                debit("742.34", MANAGEMENT_COMPANY, "Valitsemistasu 02/26")))
        .isFalse();
  }

  private static StatementDebit feeDebit() {
    return debit("-742.34", MANAGEMENT_COMPANY, "Valitsemistasu 02.-28.02.26");
  }

  private static StatementDebit debit(String amount, String beneficiaryName, String description) {
    return new StatementDebit(
        "seb-entry",
        new BigDecimal(amount),
        "EE222222222222222222",
        beneficiaryName,
        description,
        null);
  }
}
