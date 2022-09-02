package com.cubetrek.registration;

import com.cubetrek.database.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

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
        String subject = "Registration Confirmation";
        String confirmationUrl = "/registrationConfirm?token=" + token;

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(recipientAddress);
        email.setSubject(subject);
        email.setText("click to confirm your email:" + "\r\n" + "http://localhost:8080" + confirmationUrl);
        mailSender.send(email);
    }
}