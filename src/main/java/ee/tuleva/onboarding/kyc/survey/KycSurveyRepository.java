package ee.tuleva.onboarding.kyc.survey;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycSurveyRepository extends JpaRepository<KycSurvey, UUID> {

  Optional<KycSurvey> findFirstByUserIdOrderByCreatedTimeDesc(Long userId);
}
