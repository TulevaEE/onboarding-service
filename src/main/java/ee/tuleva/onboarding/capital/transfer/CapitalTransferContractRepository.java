package ee.tuleva.onboarding.capital.transfer;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapitalTransferContractRepository
    extends JpaRepository<CapitalTransferContract, Long> {

  List<CapitalTransferContract> findAllBySellerId(Long sellerMemberId);

  List<CapitalTransferContract> findAllByBuyerId(Long buyerMemberId);

  List<CapitalTransferContract> findAllByState(CapitalTransferContractState state);
}
