package ee.tuleva.onboarding.user.response;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record SecondPillarContributionsResponse(
    boolean contributing,
    @Nullable LocalDate restartDate,
    @Nullable LocalDate rejoinPossibleFrom) {}
