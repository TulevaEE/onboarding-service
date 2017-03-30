package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.onboarding.mandate.MandateService;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/*
Temporary class to get Mandate XML message
*/


@RequiredArgsConstructor
@Slf4j
@Service
public class MandateXmlService {

    private final MandateService mandateService;

    public List<String> getRequestContents(User user, Long mandateId) {
        return mandateService.getMandateFiles(mandateId, user).stream().map( signatureFile -> {
            return Jsoup.parse(
                    new String(signatureFile.content, StandardCharsets.UTF_8)
            ).head().getElementById("avaldus").html();
        }).collect(Collectors.toList());
    }
}
