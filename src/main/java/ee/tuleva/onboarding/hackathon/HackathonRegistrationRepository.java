package ee.tuleva.onboarding.hackathon;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HackathonRegistrationRepository
    extends JpaRepository<HackathonRegistration, Long> {

  Optional<HackathonRegistration> findByUserId(Long userId);
}
