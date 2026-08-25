package ee.tuleva.onboarding.notification.email.firstpayment;

import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThirdPillarPaymentArrivedClaims {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean claim(String personalCode) {
    try {
      jdbcClient
          .sql(
              """
              INSERT INTO third_pillar_payment_arrived_claim (personal_code, created_at)
              VALUES (:personalCode, :createdAt)
              """)
          .param("personalCode", personalCode)
          .param("createdAt", OffsetDateTime.now(clock))
          .update();
      return true;
    } catch (DuplicateKeyException e) {
      log.info("Payment arrived email already claimed, skipping: personalCode={}", personalCode);
      return false;
    }
  }
}
