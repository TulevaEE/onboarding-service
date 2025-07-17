package ee.tuleva.onboarding.member;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.HEAD;

import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.member.MemberRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/v1")
public class MemberController {

  private final MemberRepository memberRepository;

  @Operation(summary = "Get total count of members")
  @RequestMapping(method = HEAD, value = "/members")
  public ResponseEntity<Void> head() {
    long memberCount = memberRepository.count();

    HttpHeaders headers = new HttpHeaders();
    headers.add("x-total-count", String.valueOf(memberCount));

    return new ResponseEntity<>(null, headers, HttpStatus.OK);
  }

  @Operation(summary = "Get member by personal code")
  @RequestMapping(method = GET, value = "/members/lookup")
  public ResponseEntity<Member> getMemberByPersonalCode(@RequestParam String personalCode) {
    return memberRepository
        .findByUserPersonalCode(personalCode)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
