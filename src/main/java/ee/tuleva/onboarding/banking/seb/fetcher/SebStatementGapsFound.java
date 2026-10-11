package ee.tuleva.onboarding.banking.seb.fetcher;

import java.util.List;

public record SebStatementGapsFound(List<StatementGap> gaps) {}
