package ee.tuleva.onboarding.notification.email.mailchimp;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class CrmMailchimpRepository {

  private final JdbcClient jdbcClient;

  public Optional<String> findPersonalCodeByEmail(String email) {
    return jdbcClient
        .sql("SELECT isikukood FROM analytics.mv_crm_mailchimp WHERE email = :email")
        .param("email", email)
        .query(String.class)
        .optional();
  }
}
