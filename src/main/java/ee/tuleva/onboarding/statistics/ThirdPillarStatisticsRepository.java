package ee.tuleva.onboarding.statistics;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ThirdPillarStatisticsRepository
    extends JpaRepository<ThirdPillarStatistics, Long> {}
