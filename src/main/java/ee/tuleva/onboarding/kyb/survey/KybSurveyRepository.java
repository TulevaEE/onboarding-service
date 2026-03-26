package ee.tuleva.onboarding.kyb.survey;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface KybSurveyRepository extends JpaRepository<KybSurvey, UUID> {}
