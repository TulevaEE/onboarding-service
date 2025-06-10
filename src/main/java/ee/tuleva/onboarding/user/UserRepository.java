package ee.tuleva.onboarding.user;

import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, Long> {

  @NotNull
  Optional<User> findByPersonalCode(@NotNull String personalCode);

  @NotNull
  Optional<User> findByEmail(@NotNull String email);

  @NotNull
  @EntityGraph(attributePaths = "member")
  Optional<User> findByMember_Id(@NotNull Long memberId);
}
