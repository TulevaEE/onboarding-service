package ee.tuleva.onboarding.savings.fund.taxreport;

import ee.tuleva.onboarding.iban.IbanValidator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvestmentAccountService {

  private final InvestmentAccountRepository investmentAccountRepository;

  public Optional<String> declaredIban(String personalCode) {
    return investmentAccountRepository.findById(personalCode).map(InvestmentAccount::getIban);
  }

  @Transactional
  public InvestmentAccount declare(String personalCode, String iban) {
    if (!IbanValidator.isValid(iban)) {
      throw new IllegalArgumentException("Invalid investment account iban");
    }
    String canonicalIban = IbanValidator.canonicalize(iban);
    investmentAccountRepository.declareIban(personalCode, canonicalIban);
    return new InvestmentAccount(personalCode, canonicalIban);
  }
}
