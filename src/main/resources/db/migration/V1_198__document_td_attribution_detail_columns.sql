-- Document the per-instrument detail column semantics so dashboard authors do not try to
-- reconcile them by multiplication. weight_dev_contribution is the daily-summed covariance
-- of weight deviation and return (then scaled to the geometric TD), NOT
-- (avg_actual_weight - model_weight) * security_return — those differ by the intra-month
-- covariance term and may even differ in sign.
COMMENT ON COLUMN investment_td_attribution_detail.model_weight IS
    'Period daily-average model (target) weight, normalized to securities-only NAV.';
COMMENT ON COLUMN investment_td_attribution_detail.avg_actual_weight IS
    'Period daily-average actual weight, normalized to securities-only NAV.';
COMMENT ON COLUMN investment_td_attribution_detail.security_return IS
    'Geometric period return of the instrument: product of (1 + daily return) - 1.';
COMMENT ON COLUMN investment_td_attribution_detail.weight_dev_contribution IS
    'Contribution to geometric TD = scaling_factor * sum of daily (normalized_weight_diff * daily_return). Daily-summed covariance, so it does NOT equal (avg_actual_weight - model_weight) * security_return.';
