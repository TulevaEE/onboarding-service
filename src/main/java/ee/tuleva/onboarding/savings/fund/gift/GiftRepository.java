package ee.tuleva.onboarding.savings.fund.gift;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GiftRepository extends JpaRepository<Gift, UUID> {

  List<Gift> findByGiftLinkId(UUID giftLinkId);

  List<Gift> findByDescriptionIn(List<String> descriptions);

  Optional<Gift> findFirstByDescription(String description);
}
