package ee.tuleva.onboarding.swedbank.http;

import ee.swedbank.gateway.response.B4B;

public record SwedbankGatewayResponse(
    B4B response, String messageTrackingId, String responseTrackingId) {}
