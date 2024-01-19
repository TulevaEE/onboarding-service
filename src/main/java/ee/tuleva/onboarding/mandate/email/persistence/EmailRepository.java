package ee.tuleva.onboarding.mandate.email.persistence;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface EmailRepository extends CrudRepository<Email, Long> {

  List<Email> findAllByUserIdAndTypeAndStatusOrderByCreatedDateDesc(
      long userId, EmailType type, EmailStatus status);
}
