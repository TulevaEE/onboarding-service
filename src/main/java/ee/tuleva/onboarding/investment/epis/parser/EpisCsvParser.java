package ee.tuleva.onboarding.investment.epis.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class EpisCsvParser {

  private static final int HEADER_SCAN_ROWS = 10;

  public EpisCsv parse(String content, String headerMarker) {
    return parse(content, headerMarker, 1);
  }

  public EpisCsv parse(String content, String headerMarker, int headerRowCount) {
    List<String> lines = content.lines().toList();
    char delimiter = detectDelimiter(lines);
    int headerLineIndex = findHeaderLineIndex(lines, delimiter, headerMarker);
    List<String> headers = readHeaders(lines, delimiter, headerLineIndex, headerRowCount);

    List<Map<String, String>> rows = new ArrayList<>();
    for (int i = headerLineIndex + headerRowCount; i < lines.size(); i++) {
      List<String> cells = splitLine(lines.get(i), delimiter);
      if (cells.stream().allMatch(String::isBlank)) {
        continue;
      }
      rows.add(toRow(headers, cells));
    }
    return new EpisCsv(lines.subList(0, headerLineIndex), rows);
  }

  private static List<String> readHeaders(
      List<String> lines, char delimiter, int headerLineIndex, int headerRowCount) {
    return switch (headerRowCount) {
      case 1 -> splitLine(lines.get(headerLineIndex), delimiter);
      case 2 ->
          combineHeaderRows(lines.get(headerLineIndex), lines.get(headerLineIndex + 1), delimiter);
      default ->
          throw new IllegalArgumentException(
              "Unsupported EPIS header row count: headerRowCount=" + headerRowCount);
    };
  }

  private static List<String> combineHeaderRows(String groupLine, String subLine, char delimiter) {
    List<String> groupCells = splitLine(groupLine, delimiter);
    List<String> subCells = splitLine(subLine, delimiter);
    int columnCount = Math.max(groupCells.size(), subCells.size());
    List<String> combined = new ArrayList<>();
    String lastGroup = "";
    for (int i = 0; i < columnCount; i++) {
      String rawGroup = i < groupCells.size() ? groupCells.get(i).trim() : "";
      String sub = i < subCells.size() ? subCells.get(i).trim() : "";
      String group = rawGroup.isEmpty() ? lastGroup : rawGroup;
      lastGroup = group;
      combined.add(joinHeaderParts(group, sub));
    }
    return combined;
  }

  private static String joinHeaderParts(String group, String sub) {
    if (sub.isEmpty()) {
      return group;
    }
    if (group.isEmpty()) {
      return sub;
    }
    return group + " " + sub;
  }

  public static @Nullable String findValue(Map<String, String> row, String... keywords) {
    for (String keyword : keywords) {
      String normalizedKeyword = normalize(keyword);
      String exactColumnMatch = row.get(normalizedKeyword);
      if (exactColumnMatch != null) {
        return exactColumnMatch;
      }
      String containsMatch = valueOfColumnContaining(row, normalizedKeyword);
      if (containsMatch != null) {
        return containsMatch;
      }
    }
    return null;
  }

  private static @Nullable String valueOfColumnContaining(
      Map<String, String> row, String normalizedKeyword) {
    for (Map.Entry<String, String> entry : row.entrySet()) {
      if (entry.getKey().contains(normalizedKeyword)) {
        return entry.getValue();
      }
    }
    return null;
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
