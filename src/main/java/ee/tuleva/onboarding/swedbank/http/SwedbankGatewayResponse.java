package ee.tuleva.onboarding.swedbank.http;

import ee.swedbank.gateway.iso.response.Document;
import java.util.UUID;

public record SwedbankGatewayResponse(
    Document response, String rawResponse, UUID requestTrackingId, String responseTrackingId) {}
