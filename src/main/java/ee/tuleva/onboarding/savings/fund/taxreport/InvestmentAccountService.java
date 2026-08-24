package ee.tuleva.onboarding.savings.fund.taxreport;

import ee.tuleva.onboarding.capital.transfer.iban.IbanValidator;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvestmentAccountService {

  private static final String INVALID_IBAN = "investmentAccount.iban.invalid";

  private final InvestmentAccountRepository investmentAccountRepository;
  private final Clock clock;

  public Optional<String> declaredIban(String personalCode) {
    return investmentAccountRepository
        .findByPersonalCode(personalCode)
        .map(InvestmentAccount::getIban);
  }

  @Transactional
  public InvestmentAccount declare(String personalCode, String iban) {
    String canonicalIban = IbanValidator.canonicalize(iban);

    if (!IbanValidator.isValid(canonicalIban)) {
      throw new ErrorsResponseException(
          ErrorsResponse.ofSingleError(INVALID_IBAN, "Not a valid IBAN"));
    }

    Instant now = clock.instant();
    InvestmentAccount account =
        investmentAccountRepository
            .findByPersonalCode(personalCode)
            .orElseGet(
                () ->
                    InvestmentAccount.builder().personalCode(personalCode).createdAt(now).build());

    account.setIban(canonicalIban);
    account.setUpdatedAt(now);

    return investmentAccountRepository.save(account);
  }

  @Transactional
  public void forget(String personalCode) {
    investmentAccountRepository
        .findByPersonalCode(personalCode)
        .ifPresent(investmentAccountRepository::delete);
  }
}
