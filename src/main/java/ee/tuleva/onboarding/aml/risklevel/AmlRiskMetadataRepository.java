package ee.tuleva.onboarding.aml.risklevel;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AmlRiskMetadataRepository extends JpaRepository<AmlRiskMetadata, String> {

  List<AmlRiskMetadata> findAllByRiskLevel(Integer riskLevel);
}
