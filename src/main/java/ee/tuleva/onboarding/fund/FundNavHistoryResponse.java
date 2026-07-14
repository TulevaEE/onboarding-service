package ee.tuleva.onboarding.fund;

import java.util.List;

public record FundNavHistoryResponse(String isin, List<NavValueResponse> nav) {}
