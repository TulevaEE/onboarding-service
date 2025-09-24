package ee.tuleva.onboarding.capital.event.member;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface MemberCapitalEventRepository extends CrudRepository<MemberCapitalEvent, Long> {

  @Query("select sum(e.ownershipUnitAmount) from MemberCapitalEvent e")
  BigDecimal getTotalOwnershipUnitAmount();

  List<MemberCapitalEvent> findAllByMemberId(Long memberId);

  @Query(
      "select sum(e.fiatValue) from MemberCapitalEvent e where e.member.id = :memberId and e.type = :type")
  BigDecimal getTotalFiatValueByMemberIdAndType(Long memberId, MemberCapitalEventType type);

  @Query(
      "select sum(e.ownershipUnitAmount) from MemberCapitalEvent e where e.member.id = :memberId and e.type = :type")
  BigDecimal getTotalOwnershipUnitsByMemberIdAndType(Long memberId, MemberCapitalEventType type);

  List<MemberCapitalEvent> findAll();
}
