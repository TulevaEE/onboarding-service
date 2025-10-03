package ee.tuleva.onboarding.swedbank.statement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SavingsFundAccountIdentifierTest {

  private SavingsFundAccountIdentifier identifier;

  private static final String DEPOSIT_IBAN = "EE442200221092874625";
  private static final String WITHDRAWAL_IBAN = "EE987700771001802057";
  private static final String FUND_INVESTMENT_IBAN = "EE123456789012345678";
  private static final String UNKNOWN_IBAN = "EE999999999999999999";

  @BeforeEach
  void setUp() {
    Map<String, BankAccountType> ibanToAccountTypeMap = new HashMap<>();
    ibanToAccountTypeMap.put(DEPOSIT_IBAN, BankAccountType.DEPOSIT_EUR);
    ibanToAccountTypeMap.put(WITHDRAWAL_IBAN, BankAccountType.WITHDRAWAL_EUR);
    ibanToAccountTypeMap.put(FUND_INVESTMENT_IBAN, BankAccountType.FUND_INVESTMENT_EUR);

    identifier = new SavingsFundAccountIdentifier(ibanToAccountTypeMap);
  }

  @Test
  void identifyAccountType_shouldReturnDepositEurForDepositIban() {
    var result = identifier.identifyAccountType(DEPOSIT_IBAN);

    assertThat(result).hasValue(BankAccountType.DEPOSIT_EUR);
  }

  @Test
  void identifyAccountType_shouldReturnWithdrawalEurForWithdrawalIban() {
    var result = identifier.identifyAccountType(WITHDRAWAL_IBAN);

    assertThat(result).hasValue(BankAccountType.WITHDRAWAL_EUR);
  }

  @Test
  void identifyAccountType_shouldReturnFundInvestmentEurForFundInvestmentIban() {
    var result = identifier.identifyAccountType(FUND_INVESTMENT_IBAN);

    assertThat(result).hasValue(BankAccountType.FUND_INVESTMENT_EUR);
  }

  @Test
  void identifyAccountType_shouldReturnEmptyOptionalForUnknownIban() {
    var result = identifier.identifyAccountType(UNKNOWN_IBAN);

    assertThat(result).isEmpty();
  }

  @Test
  void isAccountType_shouldReturnTrueWhenIbanMatchesType() {
    var result = identifier.isAccountType(DEPOSIT_IBAN, BankAccountType.DEPOSIT_EUR);

    assertThat(result).isTrue();
  }

  @Test
  void isAccountType_shouldReturnFalseWhenIbanDoesNotMatchType() {
    var result = identifier.isAccountType(DEPOSIT_IBAN, BankAccountType.WITHDRAWAL_EUR);

    assertThat(result).isFalse();
  }

  @Test
  void isAccountType_shouldReturnFalseForUnknownIban() {
    var result = identifier.isAccountType(UNKNOWN_IBAN, BankAccountType.DEPOSIT_EUR);

    assertThat(result).isFalse();
  }
}
