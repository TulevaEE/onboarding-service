package ee.tuleva.onboarding.savings.fund.taxreport;

import ee.tuleva.onboarding.capital.transfer.iban.IbanValidator;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvestmentAccountService {

  private static final String INVALID_IBAN = "investmentAccount.iban.invalid";

  private final InvestmentAccountRepository investmentAccountRepository;

  public Optional<String> declaredIban(String personalCode) {
    return investmentAccountRepository.findById(personalCode).map(InvestmentAccount::getIban);
  }

  @Transactional
  public InvestmentAccount declare(String personalCode, String iban) {
    String canonicalIban = IbanValidator.canonicalize(iban);

    if (!IbanValidator.isValid(canonicalIban)) {
      throw new ErrorsResponseException(
          ErrorsResponse.ofSingleError(INVALID_IBAN, "Not a valid IBAN"));
    }

    return investmentAccountRepository.save(new InvestmentAccount(personalCode, canonicalIban));
  }
}
