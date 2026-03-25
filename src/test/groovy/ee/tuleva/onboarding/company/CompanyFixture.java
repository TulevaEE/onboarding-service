package ee.tuleva.onboarding.company;

import static ee.tuleva.onboarding.company.PartyType.PERSON;
import static ee.tuleva.onboarding.company.RelationshipType.BOARD_MEMBER;

import java.util.UUID;

public class CompanyFixture {

  public static final UUID SAMPLE_COMPANY_ID =
      UUID.fromString("c56884bb-d6d6-4617-8ce9-698c6ad2db1e");
  public static final String SAMPLE_REGISTRY_CODE = "12345678";
  public static final String SAMPLE_COMPANY_NAME = "Test OÜ";

  public static Company.CompanyBuilder sampleCompany() {
    return Company.builder()
        .id(SAMPLE_COMPANY_ID)
        .registryCode(SAMPLE_REGISTRY_CODE)
        .name(SAMPLE_COMPANY_NAME);
  }

  public static CompanyParty.CompanyPartyBuilder sampleBoardMembership(String personalCode) {
    return CompanyParty.builder()
        .partyCode(personalCode)
        .partyType(PERSON)
        .companyId(SAMPLE_COMPANY_ID)
        .relationshipType(BOARD_MEMBER);
  }
}
