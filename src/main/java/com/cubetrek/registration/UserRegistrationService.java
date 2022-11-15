package com.cubetrek.registration;

import com.cubetrek.database.*;
import com.cubetrek.ExceptionHandling;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;

@Service
@Transactional
public class UserRegistrationService {

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    @Lazy
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerificationTokenRepository tokenRepository;

    public Users register(UserDto user) {
        //Let's check if user already registered with us
        if(checkIfUserExist(user.getEmail()))
            throw new ExceptionHandling.UserRegistrationException("This Email address is already registered");

        Users userField = new Users();
        userField.setName(user.getName());
        userField.setEmail(user.getEmail());
        userField.setPassword(passwordEncoder.encode(user.getPassword()));
        userField.setUserTier(Users.UserTier.FREE);
        userField.setUserRole(Users.UserRole.USER);
        userField.setMetric(user.isMetric());
        if (Set.of(TimeZone.getAvailableIDs()).contains(user.getTimezone()))
            userField.setTimezone(user.getTimezone());
        else
            userField.setTimezone("Etc/UTC");
        userField.setSharing(TrackData.Sharing.PRIVATE);
        return usersRepository.save(userField);

    }

    private boolean emailExist(String email) {
        return usersRepository.findByEmail(email) != null;
    }

    public Users getUser(String verificationToken) {
        return tokenRepository.findByToken(verificationToken).getUser();
    }

    public VerificationToken getVerificationToken(String VerificationToken) {
        return tokenRepository.findByToken(VerificationToken);
    }

    public void saveRegisteredUser(Users user) {
        usersRepository.save(user);
    }

    public void createVerificationToken(Users user, String token, Date expiryDate) {
        VerificationToken myToken = new VerificationToken();
        myToken.setToken(token);
        myToken.setUser(user);
        myToken.setExpiryDate(expiryDate);
        tokenRepository.save(myToken);
    }
    public boolean checkIfUserExist(String email) {
        return usersRepository.findByEmail(email).isPresent();
    }

    @Bean
    public PasswordEncoder getEncoder() {
        return new BCryptPasswordEncoder();
    }

    public boolean deleteTokenAndUser(Users users) {
        tokenRepository.deleteByUser(users);
        usersRepository.delete(users);
        return true;
    }

    public boolean deleteToken(Users users) {
        tokenRepository.deleteByUser(users);
        return true;
    }
}