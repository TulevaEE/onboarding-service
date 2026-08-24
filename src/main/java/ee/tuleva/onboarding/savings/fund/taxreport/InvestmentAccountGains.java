package ee.tuleva.onboarding.savings.fund.taxreport;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

@Builder
public record InvestmentAccountGains(
    String iban,
    BigDecimal totalGain,
    List<RealisedGain> redemptions,
    boolean redeemedOutsideTheAccount) {}
