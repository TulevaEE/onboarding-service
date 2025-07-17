package ee.tuleva.onboarding.member;

import ee.tuleva.onboarding.user.member.Member;

public record MemberLookupResponse(
    Long id, Integer memberNumber, String personalCode, String firstName, String lastName) {

  public static MemberLookupResponse from(Member member) {
    var user = member.getUser();
    return new MemberLookupResponse(
        member.getId(),
        member.getMemberNumber(),
        user.getPersonalCode(),
        user.getFirstName(),
        user.getLastName());
  }
}
