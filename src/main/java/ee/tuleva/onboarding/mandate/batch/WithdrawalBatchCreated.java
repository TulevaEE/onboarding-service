package ee.tuleva.onboarding.mandate.batch;

import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.pillar.Pillar;
import java.util.Set;

record WithdrawalBatchCreated(
    int age, Set<Pillar> pillars, Set<MandateType> withdrawalTypes, Long mandateBatchId) {}
