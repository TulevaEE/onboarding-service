package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static ee.tuleva.onboarding.kyb.KybKycStatus.COMPLETED;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.kyb.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AmlKybCheckEventListenerTest {

  private static final UUID COMPANY_A_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
  private static final UUID COMPANY_B_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

  private final AmlService amlService = mock(AmlService.class);
  private final CompanyRepository companyRepository = mock(CompanyRepository.class);
  private final AmlKybCheckEventListener listener =
      new AmlKybCheckEventListener(amlService, companyRepository);

  @Test
  void savesAmlChecksForEachKybCheckCarryingTheResolvedCompanyId() {
    given(companyRepository.findByRegistryCode("12345678"))
        .willReturn(Optional.of(company(COMPANY_A_ID, "12345678")));
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")),
            new KybCheck(SOLE_MEMBER_OWNERSHIP, false, Map.of("personalCode", "38501010001")));
    var event = event("12345678", "38501010001", checks);

    listener.onKybCheckPerformed(event);

    verify(amlService)
        .addCheck(
            argThat(
                check ->
                    check.getType() == KYB_COMPANY_ACTIVE
                        && check.isSuccess()
                        && check.getPersonalCode().equals("38501010001")
                        && COMPANY_A_ID.equals(check.getCompanyId())));
    verify(amlService)
        .addCheck(
            argThat(
                check ->
                    check.getType() == KYB_SOLE_MEMBER_OWNERSHIP
                        && !check.isSuccess()
                        && check.getPersonalCode().equals("38501010001")
                        && COMPANY_A_ID.equals(check.getCompanyId())));
  }

  @Test
  void attributesChecksToTheEventCompanyNotOtherCompaniesTheRepresentativeBelongsTo() {
    // The representative 38501010001 also represents company B (98765432), but this KYB run is for
    // company A (12345678): the check must carry company A's id and never leak across companies.
    given(companyRepository.findByRegistryCode("12345678"))
        .willReturn(Optional.of(company(COMPANY_A_ID, "12345678")));
    given(companyRepository.findByRegistryCode("98765432"))
        .willReturn(Optional.of(company(COMPANY_B_ID, "98765432")));
    var event =
        event("12345678", "38501010001", List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of())));

    listener.onKybCheckPerformed(event);

    verify(amlService).addCheck(argThat(check -> COMPANY_A_ID.equals(check.getCompanyId())));
  }

  @Test
  void leavesCompanyIdNullWhenCompanyRowIsNotYetPersisted() {
    given(companyRepository.findByRegistryCode("12345678")).willReturn(Optional.empty());
    var event =
        event("12345678", "38501010001", List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of())));

    listener.onKybCheckPerformed(event);

    verify(amlService)
        .addCheck(
            argThat(
                check -> check.getType() == KYB_COMPANY_ACTIVE && check.getCompanyId() == null));
  }

  private static Company company(UUID id, String registryCode) {
    return Company.builder().id(id).registryCode(registryCode).name("Test OÜ").build();
  }

  private static KybCheckPerformedEvent event(
      String registryCode, String personalCode, List<KybCheck> checks) {
    var company = new CompanyDto(new RegistryCode(registryCode), "Test OÜ", "62011", LegalForm.OÜ);
    var relatedPersons =
        List.of(
            boardMemberOwner(new PersonalCode(personalCode), 100.0).kycStatus(COMPLETED).build());
    return new KybCheckPerformedEvent(
        AmlKybCheckEventListenerTest.class,
        company,
        new PersonalCode(personalCode),
        relatedPersons,
        checks);
  }
}
