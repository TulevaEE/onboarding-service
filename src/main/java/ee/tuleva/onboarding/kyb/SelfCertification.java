package ee.tuleva.onboarding.kyb;

public record SelfCertification(
    boolean operatesInEstonia, boolean notSanctioned, boolean noHighRiskActivity) {}
