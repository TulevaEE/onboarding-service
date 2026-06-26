package ee.tuleva.onboarding.aml.alert;

import java.math.BigDecimal;

public record TkfVolumeAlert(
    AmlAlertType type, TkfFlowDirection direction, BigDecimal amount, String windowKey) {}
