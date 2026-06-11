package ee.tuleva.onboarding.aml.alert;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AmlTkfVolumeAlertRepository extends JpaRepository<AmlTkfVolumeAlert, Long> {

  boolean existsByPersonalIdAndAlertTypeAndDirectionAndWindowKey(
      String personalId, AmlAlertType alertType, TkfFlowDirection direction, String windowKey);
}
