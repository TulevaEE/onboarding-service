package ee.tuleva.onboarding.notification.email.auto;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.notification.email.Emailable;
import java.time.LocalDate;
import java.util.List;

public interface AutoEmailRepository<T extends Emailable & Person> {

  List<T> fetch(LocalDate startDate, LocalDate endDate);

  EmailType getEmailType();
}
