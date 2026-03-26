package ee.tuleva.onboarding.kyb.survey;

import java.time.LocalDate;
import java.util.List;

record LegalEntityData(
    ValidatedField<String> name,
    ValidatedField<String> registryCode,
    ValidatedField<String> legalForm,
    ValidatedField<LocalDate> foundingDate,
    ValidatedField<LegalEntityStatus> status,
    ValidatedField<LegalEntityAddress> address,
    ValidatedField<String> businessActivity,
    ValidatedField<String> naceCode,
    ValidatedField<List<RelatedPersonData>> relatedPersons) {}
