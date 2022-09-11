package com.cubetrek.registration;

import com.cubetrek.MainController;
import com.cubetrek.database.Users;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

@Component
public class RegistrationListener implements ApplicationListener<OnRegistrationCompleteEvent> {
    @Autowired
    private UserRegistrationService userRegistrationService;

    @Autowired
    private MessageSource messages;

    @Autowired
    private JavaMailSender mailSender;


    Logger logger = LoggerFactory.getLogger(RegistrationListener.class);

    @Override
    public void onApplicationEvent(OnRegistrationCompleteEvent event) {
        this.confirmRegistration(event);
    }

    private void confirmRegistration(OnRegistrationCompleteEvent event) {
        Users user = event.getUser();
        String token = UUID.randomUUID().toString();
        Calendar expiryDate = Calendar.getInstance();
        expiryDate.add(Calendar.HOUR_OF_DAY, 1);
        userRegistrationService.createVerificationToken(user, token, expiryDate.getTime());

        String recipientAddress = user.getEmail();
        String subject = "CubeTrek Registration Confirmation";
        String confirmationUrl = "/registrationConfirm?token=" + token;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            message.setSubject(subject);
            MimeMessageHelper messageHelper = new MimeMessageHelper(message, true);
            messageHelper.setFrom("registration@mail.cubetrek.com", "CubeTrek Registration");
            messageHelper.setTo(recipientAddress);
            messageHelper.setSubject(subject);
            messageHelper.setText("Thank you for registering to CubeTrek!<br>click to confirm your email:" + "<br>" + "http://localhost:8080" + confirmationUrl, true);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException messagingException) {
            logger.error("Error sending Email", messagingException);
        }
    }
}