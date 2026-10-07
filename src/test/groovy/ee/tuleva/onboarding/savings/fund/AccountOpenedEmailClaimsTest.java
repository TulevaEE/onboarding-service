package ee.tuleva.onboarding.savings.fund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.time.ClockConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional(propagation = NOT_SUPPORTED)
@Import({AccountOpenedEmailClaims.class, ClockConfig.class})
class AccountOpenedEmailClaimsTest {

  @Autowired AccountOpenedEmailClaims claims;
  @Autowired JdbcClient jdbcClient;

  @AfterEach
  void forgetClaims() {
    jdbcClient.sql("DELETE FROM savings_fund_account_opened_email_claim").update();
  }

  @Test
  void claimsAChildAccountOnlyOnce() {
    assertThat(claims.claim("61506150006")).isTrue();
    assertThat(claims.claim("61506150006")).isFalse();
  }

  @Test
  void claimsEachChildAccountSeparately() {
    assertThat(claims.claim("61506150006")).isTrue();
    assertThat(claims.claim("60001019906")).isTrue();
  }
}
