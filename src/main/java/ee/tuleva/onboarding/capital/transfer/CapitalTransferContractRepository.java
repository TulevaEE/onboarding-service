package ee.tuleva.onboarding.capital.transfer;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapitalTransferContractRepository
    extends JpaRepository<CapitalTransferContract, Long> {

  List<CapitalTransferContract> findAllBySellerId(Long sellerMemberId);

  List<CapitalTransferContract> findAllByBuyerId(Long buyerMemberId);

  @EntityGraph(attributePaths = {"buyer.user", "seller.user"})
  List<CapitalTransferContract> findAllByState(CapitalTransferContractState state);
}
