package ee.tuleva.onboarding.savings.fund.gift;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GiftMessageRepository extends JpaRepository<GiftMessage, UUID> {

  List<GiftMessage> findByGiftLinkId(UUID giftLinkId);

  List<GiftMessage> findByDescriptionIn(List<String> descriptions);
}
