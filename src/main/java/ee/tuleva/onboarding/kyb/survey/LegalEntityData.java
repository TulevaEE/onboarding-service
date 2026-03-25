package ee.tuleva.onboarding.kyb.survey;

import java.util.List;

record LegalEntityData(
    ValidatedField<String> name,
    ValidatedField<String> registryCode,
    ValidatedField<String> legalForm,
    ValidatedField<LegalEntityStatus> status,
    ValidatedField<String> address,
    ValidatedField<String> businessActivity,
    ValidatedField<String> naceCode,
    ValidatedField<List<RelatedPersonData>> relatedPersons) {}
