package ee.tuleva.onboarding.swedbank.http;

import java.util.UUID;

public record SwedbankGatewayResponseDto(
    String rawResponse, UUID requestTrackingId, String responseTrackingId) {}
