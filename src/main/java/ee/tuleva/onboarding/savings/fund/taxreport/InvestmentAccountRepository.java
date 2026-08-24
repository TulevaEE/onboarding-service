package ee.tuleva.onboarding.savings.fund.taxreport;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

interface InvestmentAccountRepository extends CrudRepository<InvestmentAccount, String> {

  Optional<InvestmentAccount> findByPersonalCode(String personalCode);
}
