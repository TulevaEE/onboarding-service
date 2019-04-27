package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.notification.mailchimp.MailChimpService;
import ee.tuleva.onboarding.user.exception.UserAlreadyAMemberException;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.apache.commons.lang3.StringUtils.substringBeforeLast;
import static org.apache.commons.lang3.text.WordUtils.capitalizeFully;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final MailChimpService mailChimpService;

    //TODO: replace with Optional<User>
    @Nullable
    public User getById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }

    public Optional<User> findByPersonalCode(String personalCode) {
        return userRepository.findByPersonalCode(personalCode);
    }

    public User createNewUser(User user) {
        log.info("Creating new user {}", user);
        return userRepository.save(user);
    }

    public User updateUser(String personalCode, String email, String phoneNumber) {
        User user = findByPersonalCode(personalCode).map(existingUser -> {
            existingUser.setEmail(email);
            existingUser.setPhoneNumber(phoneNumber);
            return existingUser;
        }).orElseThrow(() -> new RuntimeException("User does not exist"));

        log.info("Updating user {}", user);

        return save(user);
    }

    public User registerAsMember(Long userId, String fullName) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("No user found"));

        if (user.getMember().isPresent()) {
            throw new UserAlreadyAMemberException("User is already a member! " + userId);
        }

        Member newMember = Member.builder()
                .user(user)
                .memberNumber(memberRepository.getNextMemberNumber())
                .build();

        log.info("Registering user as new member #{}: {}", newMember.getMemberNumber(), user);

        user.setMember(newMember);
        updateNameIfMissing(user, fullName);

        return save(user);
    }

    public boolean isAMember(Long userId) {
        Optional<User> user = userRepository.findById(userId);
        return user.map(u -> u.getMember().isPresent()).orElse(false);
    }

    private User updateNameIfMissing(User user, String fullName) {
        if (!user.hasName()) {
            String firstName = capitalizeFully(substringBeforeLast(fullName, " "));
            String lastName = capitalizeFully(substringAfterLast(fullName, " "));
            log.info("Updating user name from {} {} to {} {}",
                    user.getFirstName(), user.getLastName(), firstName, lastName);
            user.setFirstName(firstName);
            user.setLastName(lastName);
        }
        return user;
    }

    public User save(User user) {
        log.info("Saving user {}", user);
        return userRepository.save(user);
    }

    public User createOrUpdateUser(String personalCode, String email, String phoneNumber) {
        if (isAMember(personalCode, email)) {
            throw new UserAlreadyAMemberException("This user is already a member: " + personalCode + " " + email);
        }

        User user = userRepository.findByPersonalCode(personalCode)
                .map(u -> {
                    u.setEmail(email);
                    u.setPhoneNumber(phoneNumber);
                    return u;
                }).orElse(
                        userRepository.findByEmail(email)
                                .map(u -> {
                                    u.setPersonalCode(personalCode);
                                    u.setPhoneNumber(phoneNumber);
                                    return u;
                                }).orElse(User.builder()
                                .personalCode(personalCode)
                                .email(email)
                                .phoneNumber(phoneNumber)
                                .active(true)
                                .build())
                );
        log.info("Creating or updating user {}", user);
        return userRepository.save(user);
    }

    private boolean isAMember(String personalCode, String email) {
        return isAMemberByPersonalCode(personalCode) || isAMemberByEmail(email);
    }

    private boolean isAMemberByPersonalCode(String personalCode) {
        Optional<User> user = userRepository.findByPersonalCode(personalCode);
        return user.map(u -> u.getMember().isPresent()).orElse(false);
    }

    private boolean isAMemberByEmail(String email) {
        Optional<User> user = userRepository.findByEmail(email);
        return user.map(u -> u.getMember().isPresent()).orElse(false);
    }

}
