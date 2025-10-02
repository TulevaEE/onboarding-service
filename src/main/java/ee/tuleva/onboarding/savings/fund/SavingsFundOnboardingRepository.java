package ee.tuleva.onboarding.savings.fund;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SavingsFundOnboardingRepository {
  private final NamedParameterJdbcTemplate jdbcTemplate;

  public boolean isOnboardingCompleted(Long userId) {
    return !jdbcTemplate
        .queryForList(
            "select 1 from savings_fund_onboarding where user_id=:user_id",
            Map.of("user_id", userId))
        .isEmpty();
  }
}
