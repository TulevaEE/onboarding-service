package ee.tuleva.onboarding.investment.epis.parser;

import ee.tuleva.onboarding.investment.epis.R45TransactionType;
import java.math.BigDecimal;

public record R45UnvaluedRow(
    String fundCode, R45TransactionType transactionType, BigDecimal units, String isin) {}
