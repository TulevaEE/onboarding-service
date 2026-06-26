package ee.tuleva.onboarding.investment.epis.parser;

import ee.tuleva.onboarding.investment.epis.R45Result;
import java.util.List;
import java.util.Map;

public record R45ParseResult(
    Map<String, R45Result> fundResults, List<R45UnvaluedRow> unvaluedRows) {}
