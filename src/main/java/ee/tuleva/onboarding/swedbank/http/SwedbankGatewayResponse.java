package ee.tuleva.onboarding.swedbank.http;

import ee.swedbank.gateway.iso.response.BankToCustomerStatementV02;

public record SwedbankGatewayResponse(
    BankToCustomerStatementV02 response,
    String rawResponse,
    String messageTrackingId,
    String responseTrackingId) {}
