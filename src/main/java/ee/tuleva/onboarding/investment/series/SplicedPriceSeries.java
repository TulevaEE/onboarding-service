package ee.tuleva.onboarding.investment.series;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SplicedPriceSeries {

  private static final String SEGMENT_KEY_SEPARATOR = "+";

  private final FundValueQueries fundValueQueries;

  public static String key(List<SeriesSegment> segments) {
    return segments.stream().map(SeriesSegment::key).collect(joining(SEGMENT_KEY_SEPARATOR));
  }

  public Optional<LocalDate> lastCloseDate(List<SeriesSegment> segments) {
    return fundValueQueries.findLastValueForFund(segments.getLast().key()).map(FundValue::date);
  }

  public List<FundValue> closes(List<SeriesSegment> segments, LocalDate from, LocalDate to) {
    var key = key(segments);
    var spliced = new ArrayList<FundValue>();
    for (int i = 0; i < segments.size(); i++) {
      var segment = segments.get(i);
      var segmentFrom = later(segment.from(), from);
      var segmentTo = earlier(dayBeforeTheNextSegment(segments, i), to);
      if (segmentFrom.isAfter(segmentTo)) {
        continue;
      }
      spliced.addAll(
          spliced.isEmpty()
              ? renamed(key, closesOf(segment.key(), segmentFrom, segmentTo))
              : continuing(key, spliced.getLast(), segment.key(), segmentFrom, segmentTo));
    }
    return List.copyOf(spliced);
  }

  private List<FundValue> continuing(
      String key,
      FundValue previousClose,
      String segmentKey,
      LocalDate segmentFrom,
      LocalDate segmentTo) {
    var closes = closesOf(segmentKey, previousClose.date(), segmentTo);
    var base =
        closes.stream()
            .filter(close -> close.date().equals(previousClose.date()))
            .filter(close -> close.value().signum() > 0)
            .findFirst()
            .orElseThrow(
                () ->
                    new SeriesSpliceRefusedException(
                        "No positive close to splice on: key=%s, date=%s"
                            .formatted(segmentKey, previousClose.date())));
    return closes.stream()
        .filter(close -> !close.date().isBefore(segmentFrom))
        .map(
            close ->
                new FundValue(
                    key,
                    close.date(),
                    previousClose
                        .value()
                        .multiply(close.value())
                        .divide(base.value(), MathContext.DECIMAL128),
                    close.provider(),
                    close.updatedAt()))
        .toList();
  }

  private List<FundValue> closesOf(String segmentKey, LocalDate from, LocalDate to) {
    return fundValueQueries.findValuesBetweenDates(segmentKey, from, to).stream()
        .filter(close -> !close.date().isBefore(from) && !close.date().isAfter(to))
        .sorted(comparing(FundValue::date))
        .toList();
  }

  private static List<FundValue> renamed(String key, List<FundValue> closes) {
    return closes.stream()
        .map(
            close ->
                new FundValue(
                    key, close.date(), close.value(), close.provider(), close.updatedAt()))
        .toList();
  }

  private static @Nullable LocalDate dayBeforeTheNextSegment(
      List<SeriesSegment> segments, int index) {
    if (index + 1 >= segments.size()) {
      return null;
    }
    var next = segments.get(index + 1).from();
    return next == null ? null : next.minusDays(1);
  }

  private static LocalDate later(@Nullable LocalDate a, LocalDate b) {
    return a == null || a.isBefore(b) ? b : a;
  }

  private static LocalDate earlier(@Nullable LocalDate a, LocalDate b) {
    return a == null || a.isAfter(b) ? b : a;
  }
}
