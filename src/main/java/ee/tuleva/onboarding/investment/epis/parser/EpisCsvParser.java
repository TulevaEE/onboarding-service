package ee.tuleva.onboarding.investment.epis.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class EpisCsvParser {

  private static final int HEADER_SCAN_ROWS = 10;
  private static final Pattern ESTONIAN_DECIMAL = Pattern.compile("^-?\\d+,\\d+$");
  private static final Pattern DATE = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");

  public EpisCsv parse(String content, String headerMarker) {
    List<String> lines = content.lines().toList();
    char delimiter = detectDelimiter(lines);
    int headerLineIndex = findHeaderLineIndex(lines, delimiter, headerMarker);

    List<String> headers = splitLine(lines.get(headerLineIndex), delimiter);
    List<Map<String, String>> rows = new ArrayList<>();
    for (int i = headerLineIndex + 1; i < lines.size(); i++) {
      List<String> cells = splitLine(lines.get(i), delimiter);
      if (cells.stream().allMatch(String::isBlank)) {
        continue;
      }
      rows.add(toRow(headers, cells));
    }
    return new EpisCsv(lines.subList(0, headerLineIndex), rows);
  }

  public static @Nullable String findValue(Map<String, String> row, String... keywords) {
    for (String keyword : keywords) {
      String normalizedKeyword = normalize(keyword);
      for (Map.Entry<String, String> entry : row.entrySet()) {
        if (entry.getKey().contains(normalizedKeyword)) {
          return entry.getValue();
        }
      }
    }
    return null;
  }

  public static @Nullable BigDecimal parseNumber(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String cleaned = value.replace("%", "").replaceAll("[\\s\\u00A0]", "");
    if (cleaned.isEmpty()) {
      return null;
    }
    if (ESTONIAN_DECIMAL.matcher(cleaned).matches() && !cleaned.contains(".")) {
      cleaned = cleaned.replace(',', '.');
    } else {
      cleaned = cleaned.replace(",", "");
    }
    try {
      return new BigDecimal(cleaned);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public static @Nullable LocalDate findDate(String line) {
    Matcher matcher = DATE.matcher(line);
    if (!matcher.find()) {
      return null;
    }
    return LocalDate.of(
        Integer.parseInt(matcher.group(3)),
        Integer.parseInt(matcher.group(2)),
        Integer.parseInt(matcher.group(1)));
  }

  static String normalize(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("\\s", "");
  }

  private static char detectDelimiter(List<String> lines) {
    return lines.stream().limit(HEADER_SCAN_ROWS).anyMatch(line -> line.contains(";")) ? ';' : ',';
  }

  private static int findHeaderLineIndex(List<String> lines, char delimiter, String headerMarker) {
    String normalizedMarker = normalize(headerMarker);
    for (int i = 0; i < Math.min(lines.size(), HEADER_SCAN_ROWS); i++) {
      boolean found =
          splitLine(lines.get(i), delimiter).stream()
              .anyMatch(cell -> normalize(cell).contains(normalizedMarker));
      if (found) {
        return i;
      }
    }
    throw new IllegalArgumentException("CSV header row not found: marker=" + headerMarker);
  }

  private static Map<String, String> toRow(List<String> headers, List<String> cells) {
    Map<String, String> row = new LinkedHashMap<>();
    for (int i = 0; i < headers.size() && i < cells.size(); i++) {
      String header = normalize(headers.get(i));
      if (!header.isBlank()) {
        row.putIfAbsent(header, cells.get(i).trim());
      }
    }
    return row;
  }

  private static List<String> splitLine(String line, char delimiter) {
    List<String> cells = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char character = line.charAt(i);
      if (character == '"') {
        inQuotes = !inQuotes;
      } else if (character == delimiter && !inQuotes) {
        cells.add(current.toString());
        current.setLength(0);
      } else {
        current.append(character);
      }
    }
    cells.add(current.toString());
    return cells;
  }
}
