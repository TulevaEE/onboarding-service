package ee.tuleva.onboarding.savings.fund;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SavingFundPaymentRepository extends CrudRepository<SavingFundPayment, UUID> {
  boolean existsByRemitterIdCodeAndDescription(String remitterIdCode, String description);

  List<SavingFundPayment> findRecentPayments(String description);
  void savePaymentData(SavingFundPayment payment); // always status CREATED
  void updatePaymentData(SavingFundPayment payment); // only Swed, does not change status

  void changeStatus(UUID paymentId, SavingFundPayment.Status newStatus);

  void attachUser(UUID paymentId, Long userId);
  List<SavingFundPayment> findPaymentsWithStatus(SavingFundPayment.Status status);

  void addReturnReason(UUID paymentId, String reason);
}
