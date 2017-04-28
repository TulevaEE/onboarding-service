package ee.tuleva.onboarding.user.member;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface MemberRepository extends CrudRepository<Member, Long> {

  @Query("select max(m.memberNumber) from Member m")
  Integer getMaxMemberNumber();

}