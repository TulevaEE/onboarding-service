package ee.tuleva.onboarding.savings.fund.taxreport;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentAccountRepositoryDeclareIbanTest {

  private static final String PERSONAL_CODE = "38888888888";
  private static final String IBAN = "EE123456789012345678";

  @Mock(answer = CALLS_REAL_METHODS)
  private InvestmentAccountRepository investmentAccountRepository;

  @Test
  void updatesAgainWhenAnotherDeclarationInsertedTheAccountFirst() {
    given(investmentAccountRepository.updateIban(PERSONAL_CODE, IBAN)).willReturn(0, 1);
    given(investmentAccountRepository.insertIfAbsent(PERSONAL_CODE, IBAN)).willReturn(0);

    investmentAccountRepository.declareIban(PERSONAL_CODE, IBAN);

    verify(investmentAccountRepository, times(2)).updateIban(PERSONAL_CODE, IBAN);
    verify(investmentAccountRepository).insertIfAbsent(PERSONAL_CODE, IBAN);
  }

  @Test
  void insertsWhenNobodyDeclaredAnAccountYet() {
    given(investmentAccountRepository.updateIban(PERSONAL_CODE, IBAN)).willReturn(0);
    given(investmentAccountRepository.insertIfAbsent(PERSONAL_CODE, IBAN)).willReturn(1);

    investmentAccountRepository.declareIban(PERSONAL_CODE, IBAN);

    verify(investmentAccountRepository).updateIban(PERSONAL_CODE, IBAN);
    verify(investmentAccountRepository).insertIfAbsent(PERSONAL_CODE, IBAN);
  }
}
