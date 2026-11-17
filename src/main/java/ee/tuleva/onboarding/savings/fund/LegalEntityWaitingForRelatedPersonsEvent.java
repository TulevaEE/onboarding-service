package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.kyb.RegistryCode;

record LegalEntityWaitingForRelatedPersonsEvent(RegistryCode registryCode, String name) {}
