package ee.tuleva.onboarding.hackathon;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HackathonIdeaRepository extends JpaRepository<HackathonIdea, Long> {

  List<HackathonIdea> findAllByUserIdOrderByCreatedTimeAsc(Long userId);
}
