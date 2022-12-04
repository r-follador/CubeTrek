package com.cubetrek.registration;

import com.cubetrek.database.*;
import com.cubetrek.ExceptionHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.transaction.Transactional;
import java.io.UnsupportedEncodingException;
import java.util.*;

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

    @Value("${cubetrek.address}")
    private String httpAddress;

    @Autowired
    private JavaMailSender mailSender;


    Logger logger = LoggerFactory.getLogger(UserRegistrationService.class);

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
        return usersRepository.findByEmail(email).isPresent();
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

    public void createVerificationToken(Users user, String token, Date expiryDate, VerificationToken.VerificationType tokenType) {
        VerificationToken myToken = new VerificationToken();
        myToken.setToken(token);
        myToken.setUser(user);
        myToken.setExpiryDate(expiryDate);
        myToken.setVerificationType(tokenType);
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


    @Async
    public void requestPasswordReset(String email) {
        if (checkIfUserExist(email)) {
            Users user = usersRepository.findByEmail(email).get();

            logger.info("Request resetting password for user id '"+user.getId()+"'");
            String token = UUID.randomUUID().toString();
            Calendar expiryDate = Calendar.getInstance();
            expiryDate.add(Calendar.HOUR_OF_DAY, 1);
            createVerificationToken(user, token, expiryDate.getTime(), VerificationToken.VerificationType.PASSWORD_RESET);

            String recipientAddress = user.getEmail();
            String subject = "CubeTrek Password Reset";
            String confirmationUrl = httpAddress + "/reset_password3?token=" + token;

            try {
                MimeMessage message = mailSender.createMimeMessage();
                message.setSubject(subject);
                MimeMessageHelper messageHelper = new MimeMessageHelper(message, true);
                messageHelper.setFrom("registration@mail.cubetrek.com", "CubeTrek Password Reset");
                messageHelper.setTo(recipientAddress);
                messageHelper.setSubject(subject);
                messageHelper.setText("Someone requested to reset your CubeTrek password!<br>Click to reset your password:" + "<br>" + confirmationUrl+"<br>If you did not send the request, you can safely ignore this email.", true);
                mailSender.send(message);
            } catch (MessagingException | UnsupportedEncodingException messagingException) {
                logger.error("Error sending Reset Email", messagingException);
            }
        } else {
            logger.info("Request to reset non-existent email address; ignore");
        }
    }

    public boolean resetPassword(String token, String password) {
        VerificationToken verificationToken = getVerificationToken(token);

        if (verificationToken == null) {
            throw new ExceptionHandling.UnnamedException("Invalid Token", "Please try again, the token is not valid.");
        }

        Users user = verificationToken.getUser();
        Calendar cal = Calendar.getInstance();
        if ((verificationToken.getExpiryDate().getTime() - cal.getTime().getTime()) <= 0) {
            deleteToken(user);
            throw new ExceptionHandling.UnnamedException("Message Expired", "Please try to reset the password again..");
        }

        user.setPassword(passwordEncoder.encode(password));
        usersRepository.saveAndFlush(user);

        deleteToken(user);
        logger.info("Successfully reset password for User id '"+user.getId()+"'");
        return true;


    }


}