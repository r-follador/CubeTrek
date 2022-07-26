package com.cubetrek.newsletter;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.NewsletterSignup;
import com.cubetrek.database.NewsletterSignupRepository;
import com.cubetrek.database.Users;
import com.cubetrek.database.UsersRepository;
import com.cubetrek.registration.UserDto;
import com.cubetrek.upload.UpdateTrackmetadataResponse;
import com.cubetrek.upload.UploadResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.sql.Date;
import java.util.regex.Pattern;

@Service
@Transactional
public class NewsletterService {

    @Autowired
    private NewsletterSignupRepository newsletterSignupRepository;

    public UpdateTrackmetadataResponse store(String email) {
        email = email.trim();
        email = email.substring(0, Math.min(email.length(), 255));
        NewsletterSignup signup = new NewsletterSignup();
        if (email.isEmpty())
            throw new ExceptionHandling.FileNotAccepted("You did not enter an email address");
        String regex = "^(.+)@(.+)$";
        Pattern pattern = Pattern.compile(regex);
        if (!pattern.matcher(email).matches())
            throw new ExceptionHandling.FileNotAccepted("That doesn't look lika a valid email address ;(");
        signup.setEmail(email);
        signup.setDate(new Date(System.currentTimeMillis()));

        newsletterSignupRepository.save(signup);
        return new UpdateTrackmetadataResponse(true); //reuse Updatetrackmetadata response
    }
}