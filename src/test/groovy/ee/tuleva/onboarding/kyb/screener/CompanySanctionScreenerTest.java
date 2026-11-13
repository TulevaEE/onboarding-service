package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_PEP;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_SANCTION;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.totalOwnershipOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.kyb.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class CompanySanctionScreenerTest {

  private final JsonMapper objectMapper = JsonMapper.builder().build();
  private final PepAndSanctionCheckService sanctionCheckService =
      mock(PepAndSanctionCheckService.class);
  private final CompanySanctionScreener screener =
      new CompanySanctionScreener(sanctionCheckService);

  private static final CompanyDto COMPANY =
      new CompanyDto(new RegistryCode("12345678"), "Test OÜ", List.of("62011"), LegalForm.OÜ);

  @Test
  void noMatchesBothChecksSucceed() {
    given(sanctionCheckService.matchCompany(any())).willReturn(emptyResponse());
    var data = companyData();

    var results = screener.screen(data);

    assertThat(results).hasSize(2);
    assertThat(results).filteredOn(c -> c.type() == COMPANY_SANCTION).allMatch(KybCheck::success);
    assertThat(results).filteredOn(c -> c.type() == COMPANY_PEP).allMatch(KybCheck::success);
  }

  @Test
  void sanctionMatchFailsSanctionCheck() {
    given(sanctionCheckService.matchCompany(any())).willReturn(responseWithMatch("sanction", "ee"));
    var data = companyData();

    var results = screener.screen(data);

    var sanctionCheck = results.stream().filter(c -> c.type() == COMPANY_SANCTION).findFirst();
    assertThat(sanctionCheck).isPresent();
    assertThat(sanctionCheck.get().success()).isFalse();
  }

  @Test
  void pepMatchFromEeaCountryDoesNotFailPepCheck() {
    given(sanctionCheckService.matchCompany(any())).willReturn(responseWithMatch("role.pep", "ee"));
    var data = companyData();

    var results = screener.screen(data);

    var pepCheck = results.stream().filter(c -> c.type() == COMPANY_PEP).findFirst();
    assertThat(pepCheck).isPresent();
    assertThat(pepCheck.get().success()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"no", "is", "li"})
  void pepMatchFromAnEeaCountryOutsideTheEuDoesNotFailPepCheck(String country) {
    var response = responseWithMatch("role.pep", country);
    given(sanctionCheckService.matchCompany(any())).willReturn(response);

    var pepChecks =
        screener.screen(companyData()).stream().filter(c -> c.type() == COMPANY_PEP).toList();

    assertThat(pepChecks)
        .containsExactly(
            new KybCheck(
                COMPANY_PEP,
                true,
                Map.of(
                    "results",
                    response.results().toString(),
                    "query",
                    response.query().toString())));
  }

  @Test
  void pepMatchFromOutsideTheEeaFailsPepCheck() {
    given(sanctionCheckService.matchCompany(any())).willReturn(responseWithMatch("role.pep", "ru"));
    var data = companyData();

    var results = screener.screen(data);

    var pepCheck = results.stream().filter(c -> c.type() == COMPANY_PEP).findFirst();
    assertThat(pepCheck).isPresent();
    assertThat(pepCheck.get().success()).isFalse();
  }

  @Test
  void mixedPepMatchesWithOneOutsideTheEeaFailPepCheck() {
    var results1 = objectMapper.createArrayNode();
    results1.add(matchNode("role.pep", "de"));
    results1.add(matchNode("role.pep", "ru"));
    given(sanctionCheckService.matchCompany(any()))
        .willReturn(new MatchResponse(results1, objectMapper.createObjectNode()));
    var data = companyData();

    var results = screener.screen(data);

    var pepCheck = results.stream().filter(c -> c.type() == COMPANY_PEP).findFirst();
    assertThat(pepCheck).isPresent();
    assertThat(pepCheck.get().success()).isFalse();
  }

  @Test
  void sanctionMatchDoesNotAffectPepCheck() {
    given(sanctionCheckService.matchCompany(any())).willReturn(responseWithMatch("sanction", "ee"));
    var data = companyData();

    var results = screener.screen(data);

    var pepCheck = results.stream().filter(c -> c.type() == COMPANY_PEP).findFirst();
    assertThat(pepCheck).isPresent();
    assertThat(pepCheck.get().success()).isTrue();
  }

  private KybCompanyData companyData() {
    var person = boardMemberOwner("38501010001", 100.0).build();
    return new KybCompanyData(
        COMPANY,
        new PersonalCode("38501010001"),
        R,
        List.of(person),
        totalOwnershipOf(List.of(person)),
        new SelfCertification(true, true, true),
        "EE",
        "Harju maakond, Tallinn, Pärnu mnt 1",
        null,
        List.of());
  }

  private MatchResponse emptyResponse() {
    return new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode());
  }

  private MatchResponse responseWithMatch(String topic, String country) {
    var results = objectMapper.createArrayNode();
    results.add(matchNode(topic, country));
    return new MatchResponse(results, objectMapper.createObjectNode());
  }

  @Test
  void nonMatchingResultsAreIgnored() {
    var results = objectMapper.createArrayNode();
    var nonMatch = objectMapper.createObjectNode();
    nonMatch.put("match", false);
    nonMatch.put("id", "Q999");
    var props = objectMapper.createObjectNode();
    props.set("topics", objectMapper.createArrayNode().add("sanction"));
    props.set("country", objectMapper.createArrayNode().add("ru"));
    nonMatch.set("properties", props);
    results.add(nonMatch);
    given(sanctionCheckService.matchCompany(any()))
        .willReturn(new MatchResponse(results, objectMapper.createObjectNode()));
    var data = companyData();

    var checkResults = screener.screen(data);

    assertThat(checkResults)
        .filteredOn(c -> c.type() == COMPANY_SANCTION)
        .allMatch(KybCheck::success);
    assertThat(checkResults).filteredOn(c -> c.type() == COMPANY_PEP).allMatch(KybCheck::success);
  }

  @Test
  void pepMatchWithNoCountryTreatedAsOutsideTheEea() {
    var results = objectMapper.createArrayNode();
    var node = objectMapper.createObjectNode();
    node.put("match", true);
    node.put("id", "Q123");
    var props = objectMapper.createObjectNode();
    props.set("topics", objectMapper.createArrayNode().add("role.pep"));
    node.set("properties", props);
    results.add(node);
    given(sanctionCheckService.matchCompany(any()))
        .willReturn(new MatchResponse(results, objectMapper.createObjectNode()));
    var data = companyData();

    var checkResults = screener.screen(data);

    var pepCheck = checkResults.stream().filter(c -> c.type() == COMPANY_PEP).findFirst();
    assertThat(pepCheck).isPresent();
    assertThat(pepCheck.get().success()).isFalse();
  }

  private ObjectNode matchNode(String topic, String country) {
    var node = objectMapper.createObjectNode();
    node.put("match", true);
    node.put("id", "Q123");
    var properties = objectMapper.createObjectNode();
    var topics = objectMapper.createArrayNode();
    topics.add(topic);
    properties.set("topics", topics);
    var countries = objectMapper.createArrayNode();
    countries.add(country);
    properties.set("country", countries);
    node.set("properties", properties);
    return node;
  }
}
