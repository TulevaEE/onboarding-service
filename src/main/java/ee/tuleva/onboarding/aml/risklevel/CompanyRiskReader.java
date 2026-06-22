package ee.tuleva.onboarding.aml.risklevel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
class CompanyRiskReader {

  private final JdbcClient jdbcClient;

  public void refreshMaterializedView() {
    log.info("Start materialized view refresh: analytics.v_company_risk_metadata");
    jdbcClient
        .sql("REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.v_company_risk_metadata")
        .update();
    log.info("Materialized view refreshed: analytics.v_company_risk_metadata");
  }
}
