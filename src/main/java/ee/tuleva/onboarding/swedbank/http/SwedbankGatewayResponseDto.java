package ee.tuleva.onboarding.swedbank.http;

public record SwedbankGatewayResponseDto(
    String rawResponse, String requestTrackingId, String responseTrackingId) {}
