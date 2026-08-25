package ee.tuleva.onboarding.savings.fund.taxreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentAccountServiceTest {

  private static final String PERSONAL_CODE = "38888888888";
  private static final String IBAN = "EE123456789012345678";

  @Mock private InvestmentAccountRepository investmentAccountRepository;

  private InvestmentAccountService investmentAccountService;

  @BeforeEach
  void setUp() {
    investmentAccountService = new InvestmentAccountService(investmentAccountRepository);
  }

  @Test
  void hasNoAccountUntilOneIsDeclared() {
    given(investmentAccountRepository.findById(PERSONAL_CODE)).willReturn(Optional.empty());

    assertThat(investmentAccountService.declaredIban(PERSONAL_CODE)).isEmpty();
  }

  @Test
  void keepsTheAccountThatWasDeclared() {
    given(investmentAccountRepository.save(any(InvestmentAccount.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    InvestmentAccount declared = investmentAccountService.declare(PERSONAL_CODE, IBAN);

    assertThat(declared.getPersonalCode()).isEqualTo(PERSONAL_CODE);
    assertThat(declared.getIban()).isEqualTo(IBAN);
  }

  @Test
  void writesAnAccountDownWithoutTheSpacesPeopleTypeIntoIt() {
    given(investmentAccountRepository.save(any(InvestmentAccount.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    InvestmentAccount declared =
        investmentAccountService.declare(PERSONAL_CODE, "ee12 3456 7890 1234 5678");

    assertThat(declared.getIban()).isEqualTo(IBAN);
  }
}
