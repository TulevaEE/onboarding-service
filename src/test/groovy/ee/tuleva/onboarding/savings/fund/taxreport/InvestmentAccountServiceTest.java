package ee.tuleva.onboarding.savings.fund.taxreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentAccountServiceTest {

  private static final String PERSONAL_CODE = "38888888888";
  private static final String IBAN = "EE651010220306497226";

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
    InvestmentAccount declared = investmentAccountService.declare(PERSONAL_CODE, IBAN);

    assertThat(declared).isEqualTo(new InvestmentAccount(PERSONAL_CODE, IBAN));
    then(investmentAccountRepository).should().declareIban(PERSONAL_CODE, IBAN);
  }

  @Test
  void refusesToDeclareAnAccountNumberThatIsNotAnIban() {
    assertThatThrownBy(
            () -> investmentAccountService.declare(PERSONAL_CODE, "EE123456789012345678"))
        .isInstanceOf(IllegalArgumentException.class);
    then(investmentAccountRepository).shouldHaveNoInteractions();
  }

  @Test
  void writesAnAccountDownWithoutTheSpacesPeopleTypeIntoIt() {
    InvestmentAccount declared =
        investmentAccountService.declare(PERSONAL_CODE, "ee65 1010 2203 0649 7226");

    assertThat(declared.getIban()).isEqualTo(IBAN);
    then(investmentAccountRepository).should().declareIban(PERSONAL_CODE, IBAN);
  }
}
