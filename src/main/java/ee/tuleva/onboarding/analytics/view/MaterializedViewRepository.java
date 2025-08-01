package ee.tuleva.onboarding.analytics.view;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterializedViewRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  private static final String[] MATERIALIZED_VIEWS = {
    "analytics.mv_change_application_history",
    "analytics.mv_tuk00_tuk75_history_new",
    "analytics.mv_new_monthly_mandates_leavers",
    "analytics.mv_third_pillar_summary",
    "analytics.mv_third_pillar_savings",
    "analytics.mv_crm",
    "analytics.mv_third_pillar_kpi",
    "analytics.mv_second_pillar_sums_new",
    "analytics.mv_kpi_new",
    "analytics.mv_monthly_conversions",
    "analytics.mv_crm_mailchimp",
    "analytics.mv_coop_list",
    "analytics.mv_tulud_kulud_tegelik",
    "analytics.mv_tulud_kulud_prognoos",
    "analytics.mv_kuu_tulemus_vs_prognoos",
    "analytics.mv_aasta_eelarve_vs_prognoos",
    "analytics.mv_ytd_eelarve_vs_tulemus"
  };

  public void refreshAllViews() {
    for (String view : MATERIALIZED_VIEWS) {
      refreshMaterializedView(view);
    }
  }

  private void refreshMaterializedView(String viewName) {
    log.info("Start materialized view refresh: {}", viewName);
    String sql = "REFRESH MATERIALIZED VIEW " + viewName + ";";
    try {
      jdbcTemplate.getJdbcOperations().execute(sql);
      log.info("Materialized view refreshed: {}", viewName);
    } catch (Exception e) {
      log.error("Failed to refresh materialized view: {}. Error: {}", viewName, e.getMessage(), e);
      throw new RuntimeException("Failed to refresh materialized view: " + viewName, e);
    }
  }
}
