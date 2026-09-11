package ee.tuleva.onboarding.savings.fund.taxreport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class InvestmentAccountRepositoryTest {

  private static final String PERSONAL_CODE = "38888888888";
  private static final String IBAN = "EE123456789012345678";
  private static final String ANOTHER_IBAN = "EE987654321098765432";

  @Autowired private InvestmentAccountRepository investmentAccountRepository;

  @Test
  void declaresAnAccountForSomeoneWhoHasNoneYet() {
    investmentAccountRepository.declareIban(PERSONAL_CODE, IBAN);

    assertThat(investmentAccountRepository.findAll())
        .containsExactly(new InvestmentAccount(PERSONAL_CODE, IBAN));
  }

  @Test
  void replacesThePreviouslyDeclaredAccount() {
    investmentAccountRepository.declareIban(PERSONAL_CODE, IBAN);

    investmentAccountRepository.declareIban(PERSONAL_CODE, ANOTHER_IBAN);

    assertThat(investmentAccountRepository.findAll())
        .containsExactly(new InvestmentAccount(PERSONAL_CODE, ANOTHER_IBAN));
  }

  @Test
  void insertsNothingWhenAnotherDeclarationWonTheRace() {
    investmentAccountRepository.declareIban(PERSONAL_CODE, IBAN);

    int inserted = investmentAccountRepository.insertIfAbsent(PERSONAL_CODE, ANOTHER_IBAN);

    assertThat(inserted).isZero();
    assertThat(investmentAccountRepository.findAll())
        .containsExactly(new InvestmentAccount(PERSONAL_CODE, IBAN));
  }
}
