package ee.tuleva.onboarding.hackathon;

import java.time.Instant;
import java.util.List;

public record HackathonIdeasDto(
    boolean open, Instant deadline, boolean registered, List<HackathonIdeaDto> ideas) {}
