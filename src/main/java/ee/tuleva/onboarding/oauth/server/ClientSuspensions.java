package ee.tuleva.onboarding.oauth.server;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ClientSuspensions {

  private final JdbcClient jdbcClient;

  boolean isSuspended(String clientId) {
    return jdbcClient
            .sql("SELECT COUNT(*) FROM oauth_client_suspension WHERE client_id = :clientId")
            .param("clientId", clientId)
            .query(Long.class)
            .single()
        > 0;
  }
}
