package ee.tuleva.onboarding.capital.event.member;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.math.BigDecimal;
import java.util.List;

public interface MemberCapitalEventRepository extends CrudRepository<MemberCapitalEvent, Long> {

    @Query("select sum(m.ownershipUnitAmount) from MemberCapitalEvent m")
    BigDecimal getTotalOwnershipUnitAmount();

    List<MemberCapitalEvent> findAllByMemberId(Long memberId);

    List<MemberCapitalEvent> findAll();
}
