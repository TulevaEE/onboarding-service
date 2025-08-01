package ee.tuleva.onboarding.analytics.view;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    when(jdbcTemplate.getJdbcOperations()).thenReturn(jdbcOperations);
    repository = new MaterializedViewRepository(jdbcTemplate);
  }

  @Test
  @DisplayName("refreshAllViews should execute refresh for each view")
  void refreshAllViews_executesRefreshForEachView() {
    // when
    repository.refreshAllViews();

    // then
    verify(jdbcOperations, times(17)).execute(anyString());
    verify(jdbcOperations)
        .execute("REFRESH MATERIALIZED VIEW analytics.mv_change_application_history;");
    verify(jdbcOperations)
        .execute("REFRESH MATERIALIZED VIEW analytics.mv_tuk00_tuk75_history_new;");
    verify(jdbcOperations)
        .execute("REFRESH MATERIALIZED VIEW analytics.mv_new_monthly_mandates_leavers;");
    verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_third_pillar_summary;");
    verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_third_pillar_savings;");
    verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_crm;");
    verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_third_pillar_kpi;");
    verify(jdbcOperations)
        .execute("REFRESH MATERIALIZED VIEW analytics.mv_second_pillar_sums_new;");
    verify(jdbcOperations).execute("REFRESH MATERIALIZED VIEW analytics.mv_kpi_new;");
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
  @DisplayName("refreshAllViews should throw exception when refresh fails")
  void refreshAllViews_throwsExceptionWhenRefreshFails() {
    // given
    doThrow(new DataAccessException("Permission denied") {})
        .when(jdbcOperations)
        .execute(anyString());

    // when & then
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> repository.refreshAllViews());
    assertTrue(thrown.getMessage().contains("Failed to refresh materialized view"));
  }
}
