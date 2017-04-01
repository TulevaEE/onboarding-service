package ee.tuleva.onboarding.mandate;

import com.codeborne.security.mobileid.SignatureFile;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.content.MandateContentCreator;
import ee.tuleva.onboarding.user.CsdUserPreferencesService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Component
@RequiredArgsConstructor
@Slf4j
public class MandateFileService {

    private final MandateRepository mandateRepository;
    private final FundRepository fundRepository;
    private final CsdUserPreferencesService csdUserPreferencesService;
    private final MandateContentCreator mandateContentCreator;

    public List<SignatureFile> getMandateFiles(Long mandateId, User user) {
        Mandate mandate = mandateRepository.findByIdAndUser(mandateId, user);

        List<Fund> funds = new ArrayList<>();
        fundRepository.findAll().forEach(funds::add);

        UserPreferences userPreferences = csdUserPreferencesService.getPreferences(user.getPersonalCode());
        userPreferences = checkUserPreferences(userPreferences);

        return mandateContentCreator.getContentFiles(user, mandate, funds, userPreferences)
                .stream()
                .map(file -> new SignatureFile(file.getName(), file.getMimeType(), file.getContent()))
                .collect(toList());
    }

    private UserPreferences checkUserPreferences(UserPreferences userPreferences) {
        UserPreferences defaultUserPreferences = UserPreferences.defaultUserPreferences();
        if(Arrays.asList(
                userPreferences.getAddressRow1(),
                userPreferences.getAddressRow2(),
                userPreferences.getCountry(),
                userPreferences.getDistrictCode(),
                userPreferences.getPostalIndex())
                .stream()
                .filter( str -> str == null || str.isEmpty())
                .count() > 0) {

            userPreferences.setAddressRow1(defaultUserPreferences.getAddressRow1());
            userPreferences.setAddressRow2(defaultUserPreferences.getAddressRow2());
            userPreferences.setAddressRow3(defaultUserPreferences.getAddressRow3());
            userPreferences.setCountry(defaultUserPreferences.getCountry());
            userPreferences.setDistrictCode(defaultUserPreferences.getDistrictCode());
            userPreferences.setPostalIndex(defaultUserPreferences.getPostalIndex());
        }


        if(userPreferences.getContactPreference() == null) {
            userPreferences.setContactPreference(defaultUserPreferences.getContactPreference());
        }

        if(userPreferences.getLanguagePreference() == null) {
            userPreferences.setLanguagePreference(defaultUserPreferences.getLanguagePreference());
        }

        if(userPreferences.getNoticeNeeded() == null) {
            userPreferences.setNoticeNeeded(defaultUserPreferences.getNoticeNeeded());
        }

        return userPreferences;
    }
}
