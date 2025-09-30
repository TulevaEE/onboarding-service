package ee.tuleva.onboarding.savings.fund;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SavingFundPaymentRepository extends CrudRepository<SavingFundPayment, UUID> {}
