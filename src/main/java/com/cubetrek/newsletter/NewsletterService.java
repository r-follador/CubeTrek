package com.cubetrek.newsletter;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.NewsletterSignup;
import com.cubetrek.database.NewsletterSignupRepository;
import com.cubetrek.upload.UpdateTrackmetadataResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.sql.Date;
import java.util.regex.Pattern;

@Service
@Transactional
public class NewsletterService {

    @Autowired
    private NewsletterSignupRepository newsletterSignupRepository;

    Logger logger = LoggerFactory.getLogger(NewsletterService.class);

    public UpdateTrackmetadataResponse store(String email) {
        email = email.trim();
        email = email.substring(0, Math.min(email.length(), 255));
        NewsletterSignup signup = new NewsletterSignup();
        if (email.isEmpty())
            throw new ExceptionHandling.FileNotAccepted("You did not enter an email address");
        String regex = "^(.+)@(.+)$";
        Pattern pattern = Pattern.compile(regex);
        if (!pattern.matcher(email).matches())
            throw new ExceptionHandling.FileNotAccepted("That doesn't look like a valid email address ;(");
        signup.setEmail(email);
        signup.setDate(new Date(System.currentTimeMillis()));
        logger.info("Saving email address - Successful");
        newsletterSignupRepository.save(signup);
        return new UpdateTrackmetadataResponse(true); //reuse Updatetrackmetadata response
    }
}