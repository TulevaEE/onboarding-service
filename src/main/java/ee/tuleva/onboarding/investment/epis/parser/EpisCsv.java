package ee.tuleva.onboarding.investment.epis.parser;

import java.util.List;
import java.util.Map;

public record EpisCsv(List<String> preHeaderLines, List<Map<String, String>> rows) {}
