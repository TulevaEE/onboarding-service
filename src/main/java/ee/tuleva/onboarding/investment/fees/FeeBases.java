package ee.tuleva.onboarding.investment.fees;

import java.math.BigDecimal;

/**
 * The two amounts a day's fees are charged on. They are different numbers because the contracts
 * behind them are different documents.
 *
 * <p>{@code navFeeBase} is the fund's net asset value — every asset less every non-fee liability,
 * per Tingimused 18.2.1 — and is what the management fee is charged on.
 *
 * <p>{@code assetValue} is "Fondi aktivate turuväärtuste summa" from the Depooleping: the asset
 * side on its own, with no liability netted off. Netting them would give netovara, which is a
 * different word and a smaller number, so the depot fee cannot reuse navFeeBase.
 *
 * <p>Both come from the same NAV components in the same calculation, so they are the values as of
 * the moment the NAV was recorded. Deriving either from nav_report instead would read the
 * <i>previous</i> day's calculation, because NavPublisher writes those rows only after the fees for
 * the day have already been accrued — and accruals are forward-only, so the lag would never be
 * repaired.
 */
public record FeeBases(BigDecimal navFeeBase, BigDecimal assetValue) {}
