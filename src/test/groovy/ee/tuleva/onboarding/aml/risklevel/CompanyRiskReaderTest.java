package ee.tuleva.onboarding.aml.risklevel;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class CompanyRiskReaderTest {

  private JdbcClient jdbcClient;
  private JdbcClient.StatementSpec statementSpec;
  private CompanyRiskReader reader;

  @BeforeEach
  void setUp() {
    jdbcClient = mock(JdbcClient.class);
    statementSpec = mock(JdbcClient.StatementSpec.class);
    when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
    when(statementSpec.update()).thenReturn(0);
    reader = new CompanyRiskReader(jdbcClient);
  }

  @Test
  void refreshMaterializedView_refreshesCompanyRiskView() {
    reader.refreshMaterializedView();

    verify(jdbcClient)
        .sql("REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.v_company_risk_metadata");
  }
}
