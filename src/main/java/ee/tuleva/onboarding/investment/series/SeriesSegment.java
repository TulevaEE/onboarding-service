package ee.tuleva.onboarding.investment.series;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record SeriesSegment(String key, @Nullable LocalDate from) {}
