package ee.tuleva.onboarding.mandate;

import com.codeborne.security.mobileid.SignatureFile;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.content.MandateContentCreator;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Component
@RequiredArgsConstructor
@Slf4j
public class MandateFileService {

    private final MandateRepository mandateRepository;
    private final FundRepository fundRepository;
    private final EpisService episService;
    private final MandateContentCreator mandateContentCreator;
    private final UserService userService;

    public List<SignatureFile> getMandateFiles(Long mandateId, Long userId) {
        User user = userService.getById(userId);
        Mandate mandate = mandateRepository.findByIdAndUserId(mandateId, userId);

        List<Fund> funds = fundRepository.findAllByPillar(mandate.getPillar());

        UserPreferences userPreferences = episService.getContactDetails(user);
        userPreferences = checkUserPreferences(userPreferences);

        return mandateContentCreator.getContentFiles(user, mandate, funds, userPreferences)
            .stream()
            .map(file -> new SignatureFile(file.getName(), file.getMimeType(), file.getContent()))
            .collect(toList());
    }

    private UserPreferences checkUserPreferences(UserPreferences userPreferences) {
        UserPreferences defaultUserPreferences = UserPreferences.defaultUserPreferences();
        if (Stream.of(
            userPreferences.getAddressRow1(),
            userPreferences.getAddressRow2(),
            userPreferences.getCountry(),
            userPreferences.getDistrictCode(),
            userPreferences.getPostalIndex()).anyMatch(str -> str == null || str.isEmpty())) {

            userPreferences.setAddressRow1(defaultUserPreferences.getAddressRow1());
            userPreferences.setAddressRow2(defaultUserPreferences.getAddressRow2());
            userPreferences.setAddressRow3(defaultUserPreferences.getAddressRow3());
            userPreferences.setCountry(defaultUserPreferences.getCountry());
            userPreferences.setDistrictCode(defaultUserPreferences.getDistrictCode());
            userPreferences.setPostalIndex(defaultUserPreferences.getPostalIndex());
        }


        if (userPreferences.getContactPreference() == null) {
            userPreferences.setContactPreference(defaultUserPreferences.getContactPreference());
        }

        if (userPreferences.getLanguagePreference() == null) {
            userPreferences.setLanguagePreference(defaultUserPreferences.getLanguagePreference());
        }

        if (userPreferences.getNoticeNeeded() == null) {
            userPreferences.setNoticeNeeded(defaultUserPreferences.getNoticeNeeded());
        }

        return userPreferences;
    }
}
