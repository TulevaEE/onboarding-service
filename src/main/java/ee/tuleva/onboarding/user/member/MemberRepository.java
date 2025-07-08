package ee.tuleva.onboarding.user.member;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface MemberRepository extends CrudRepository<Member, Long> {

  @Query("select max(m.memberNumber) + 1 from Member m")
  Integer getNextMemberNumber();

  Optional<Member> findByUserPersonalCode(String personalCode);
}
