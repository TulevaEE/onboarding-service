package ee.tuleva.onboarding.aml;

import java.util.List;

record ScreeningResult(List<AmlCheck> checks, boolean failed) {}
