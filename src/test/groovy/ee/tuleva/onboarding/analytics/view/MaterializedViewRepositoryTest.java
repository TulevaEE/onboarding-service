package ee.tuleva.onboarding.analytics.view;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class MaterializedViewRepositoryTest {

  @Mock private NamedParameterJdbcTemplate jdbcTemplate;

  @Mock private JdbcOperations jdbcOperations;

  private MaterializedViewRepository repository;

  @BeforeEach
  void setUp() {
    given(jdbcTemplate.getJdbcOperations()).willReturn(jdbcOperations);
    repository = new MaterializedViewRepository(jdbcTemplate);
  }

  @Test
  void refreshesSnapshotsBeforeTheViewsThatReadThem() {
    repository.refreshAllViews();

    verify(jdbcOperations, times(21)).execute(anyString());
    InOrder inOrder = inOrder(jdbcOperations);
    inOrder.verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_tuk75_api;");
    inOrder.verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_tuk00_api;");
    inOrder
        .verify(jdbcOperations)
        .execute("REFRESH MATERIALIZED VIEW analytics.mv_third_pillar_api;");
    inOrder
        .verify(jdbcOperations)
        .execute("REFRESH MATERIALIZED VIEW analytics.mv_third_pillar_history;");
    inOrder
        .verify(jdbcOperations)
        .execute("REFRESH MATERIALIZED VIEW analytics.mv_change_application_history;");
    inOrder
        .verify(jdbcOperations)
        .execute("REFRESH MATERIALIZED VIEW analytics.mv_new_monthly_mandates_leavers;");
    inOrder.verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_kpi_new;");
    verify(jdbcOperations)
        .execute("REFRESH MATERIALIZED VIEW analytics.mv_tuk00_tuk75_history_new;");
    verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_third_pillar_summary;");
    verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_third_pillar_savings;");
    verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_crm;");
    verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_third_pillar_kpi;");
    verify(jdbcOperations)
        .execute("REFRESH MATERIALIZED VIEW analytics.mv_second_pillar_sums_new;");
    verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_monthly_conversions;");
    verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_crm_mailchimp;");
    verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_coop_list;");
    verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_tulud_kulud_tegelik;");
    verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_tulud_kulud_prognoos;");
    verify(jdbcOperations)
        .execute("REFRESH MATERIALIZED VIEW analytics.mv_kuu_tulemus_vs_prognoos;");
    verify(jdbcOperations)
        .execute("REFRESH MATERIALIZED VIEW analytics.mv_aasta_eelarve_vs_prognoos;");
    verify(jdbcOperations)
        .execute("REFRESH MATERIALIZED VIEW analytics.mv_ytd_eelarve_vs_tulemus;");
  }

  @Test
  void abortsTheBatchWhenARefreshFails() {
    willThrow(new DataAccessException("Permission denied") {})
        .given(jdbcOperations)
        .execute(anyString());

    assertThatThrownBy(() -> repository.refreshAllViews()).isInstanceOf(RuntimeException.class);

    verify(jdbcOperations, times(1)).execute(anyString());
  }
}
