package ee.tuleva.onboarding.analytics.thirdpillar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsRecentThirdPillarRepository
    extends JpaRepository<AnalyticsRecentThirdPillar, Long> {}
