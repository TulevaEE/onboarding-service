package ee.tuleva.onboarding.banking.seb;

import ee.tuleva.onboarding.config.TestSchedulerLockConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@TestPropertySource(
    properties = {
      "seb-gateway.enabled=true",
      "seb-gateway.url=https://test.example.com",
      "seb-gateway.orgId=test-org",
      "seb-gateway.keystore.path=src/test/resources/banking/seb/test-seb-gateway.p12",
      "seb-gateway.keystore.password=testpass",
      "seb-gateway.reconciliation-delay=0s",
      "seb-gateway.accounts.DEPOSIT_EUR=EE001234567890123456",
      "seb-gateway.accounts.WITHDRAWAL_EUR=EE001234567890123457",
      "seb-gateway.accounts.FUND_INVESTMENT_EUR=EE001234567890123458"
    })
@Import({TestSchedulerLockConfiguration.class, TestSebSchedulerConfiguration.class})
@Transactional
public @interface SebIntegrationTest {}
