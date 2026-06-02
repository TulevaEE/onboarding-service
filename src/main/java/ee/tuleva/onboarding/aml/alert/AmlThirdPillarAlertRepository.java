package ee.tuleva.onboarding.aml.alert;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AmlThirdPillarAlertRepository extends JpaRepository<AmlThirdPillarAlert, Long> {

  boolean existsByTransactionIdAndAlertType(Long transactionId, AmlAlertType alertType);
}
