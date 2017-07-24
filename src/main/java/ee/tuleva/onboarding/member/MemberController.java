package ee.tuleva.onboarding.member;

import ee.tuleva.onboarding.user.member.MemberRepository;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.StreamSupport;

import static org.springframework.web.bind.annotation.RequestMethod.HEAD;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/v1")
public class MemberController {

    private final MemberRepository memberRepository;

    @ApiOperation(value = "Get total count of members")
    @RequestMapping(method = HEAD, value = "/members")
    public ResponseEntity head() {
        Long memberCount =
                StreamSupport.stream(memberRepository.findAll().spliterator(), false)
                        .count();

        HttpHeaders headers = new HttpHeaders();
        headers.add("x-total-count", String.valueOf(memberCount));

        return new ResponseEntity<>(null, headers, HttpStatus.OK);
    }

}
