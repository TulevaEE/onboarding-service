package ee.tuleva.onboarding.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

@ExtendWith(MockitoExtension.class)
class InvestorStatisticsRepositoryTest {

  @Mock private JdbcClient jdbcClient;
  @InjectMocks private InvestorStatisticsRepository repository;
  @Captor private ArgumentCaptor<String> sqlCaptor;

  @Test
  @SuppressWarnings("unchecked")
  void getActiveInvestorCount_readsLatestTotalActiveInvestorsFromKpiView() {
    StatementSpec statementSpec = mock(StatementSpec.class);
    MappedQuerySpec<Long> mappedQuerySpec = mock(MappedQuerySpec.class);

    given(jdbcClient.sql(sqlCaptor.capture())).willReturn(statementSpec);
    given(statementSpec.query(Long.class)).willReturn(mappedQuerySpec);
    given(mappedQuerySpec.single()).willReturn(85224L);

    long count = repository.getActiveInvestorCount();

    assertThat(count).isEqualTo(85224L);

    String sql = sqlCaptor.getValue();
    assertThat(sql).contains("analytics.mv_kpi_new");
    assertThat(sql).contains("COALESCE(SUM(total_active_investors), 0)");
    assertThat(sql)
        .contains("WHERE reporting_date = (SELECT MAX(reporting_date) FROM analytics.mv_kpi_new)");
  }

  @Test
  @SuppressWarnings("unchecked")
  void getPreviousActiveInvestorCount_readsSecondMostRecentReportingDate() {
    StatementSpec statementSpec = mock(StatementSpec.class);
    MappedQuerySpec<Long> mappedQuerySpec = mock(MappedQuerySpec.class);

    given(jdbcClient.sql(sqlCaptor.capture())).willReturn(statementSpec);
    given(statementSpec.query(Long.class)).willReturn(mappedQuerySpec);
    given(mappedQuerySpec.optional()).willReturn(Optional.of(84000L));

    OptionalLong previous = repository.getPreviousActiveInvestorCount();

    assertThat(previous).hasValue(84000L);
    String sql = sqlCaptor.getValue();
    assertThat(sql).doesNotContain("COALESCE");
    assertThat(sql)
        .contains("reporting_date < (SELECT MAX(reporting_date) FROM analytics.mv_kpi_new)");
  }

  @Test
  @SuppressWarnings("unchecked")
  void getPreviousActiveInvestorCount_isEmptyWhenNoPreviousPeriod() {
    StatementSpec statementSpec = mock(StatementSpec.class);
    MappedQuerySpec<Long> mappedQuerySpec = mock(MappedQuerySpec.class);

    given(jdbcClient.sql(any())).willReturn(statementSpec);
    given(statementSpec.query(Long.class)).willReturn(mappedQuerySpec);
    given(mappedQuerySpec.optional()).willReturn(Optional.empty());

    assertThat(repository.getPreviousActiveInvestorCount()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void getPreviousActiveInvestorCount_keepsRealZero() {
    StatementSpec statementSpec = mock(StatementSpec.class);
    MappedQuerySpec<Long> mappedQuerySpec = mock(MappedQuerySpec.class);

    given(jdbcClient.sql(any())).willReturn(statementSpec);
    given(statementSpec.query(Long.class)).willReturn(mappedQuerySpec);
    given(mappedQuerySpec.optional()).willReturn(Optional.of(0L));

    assertThat(repository.getPreviousActiveInvestorCount()).hasValue(0L);
  }
}
