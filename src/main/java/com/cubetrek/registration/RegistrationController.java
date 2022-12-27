package com.cubetrek.registration;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.NewsletterSignup;
import com.cubetrek.database.NewsletterSignupRepository;
import com.cubetrek.database.Users;
import com.cubetrek.database.VerificationToken;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Calendar;

@Controller
public class RegistrationController {
    @Autowired
    private UserRegistrationService userRegistrationService;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    NewsletterSignupRepository newsletterSignupRepository;

    Logger logger = LoggerFactory.getLogger(RegistrationController.class);

    @GetMapping("/registration")
    public String showRegistrationForm(WebRequest request, Model model) {
        UserDto userDto = new UserDto();
        model.addAttribute("user", userDto);
        return "registration";
    }

    @PostMapping("/registration")
    public String registerUserAccount(
            @ModelAttribute("user") @Valid UserDto userDto, BindingResult bindingResult) {

        if (bindingResult.hasErrors())
            return "registration";

        try {
            Users registered = userRegistrationService.register(userDto);
            eventPublisher.publishEvent(new OnRegistrationCompleteEvent(registered));
        } catch (ExceptionHandling.UserRegistrationException ex) { //the email address exists already
            bindingResult.addError(new FieldError("user", "email", ex.msg));
            return "registration";
        } catch (RuntimeException ex) {
            logger.error("Registration Error", ex);
            throw new ExceptionHandling.UnnamedException("Something went wrong :(", "Could not finalize Registration, please try again later or send an email to contact@cubetrek.com");
        }

        return "successRegister";
    }

    @GetMapping("/registrationConfirm")
    public String confirmRegistration(WebRequest request, Model model, @RequestParam("token") String token) {

        VerificationToken verificationToken = userRegistrationService.getVerificationToken(token);
        if (verificationToken == null) {
            throw new ExceptionHandling.UnnamedException("Invalid Token", "Please register again, the token is not valid.");
        }

        Users user = verificationToken.getUser();
        Calendar cal = Calendar.getInstance();
        if ((verificationToken.getExpiryDate().getTime() - cal.getTime().getTime()) <= 0) {
            if (!user.isEnabled())
                userRegistrationService.deleteTokenAndUser(user);
            else
                userRegistrationService.deleteToken(user);
            throw new ExceptionHandling.UnnamedException("Message Expired", "Please try to sign up again.");
        }

        user.setEnabled(true);
        userRegistrationService.saveRegisteredUser(user);
        logger.info("User Email successfully validated: "+user.getEmail());
        userRegistrationService.deleteToken(user);
        NewsletterSignup signup = new NewsletterSignup();
        signup.setEmail(user.getEmail());
        signup.setDate(new java.sql.Date(System.currentTimeMillis()));
        newsletterSignupRepository.save(signup);
        return "redirect:/successRegisterValidation";
    }

    @GetMapping("/successRegisterValidation")
    public String successRegistration() {
        return "successRegisterValidation";
    }

    public static class Password {
        @Getter
        @Setter
        String email;
    }

    @GetMapping("/reset_password")
    public String showResetForm(WebRequest request, Model model) {
        Password pw = new Password();
        model.addAttribute("password", pw);
        return "resetPassword";
    }

    @PostMapping(value = "/reset_password")
    public String initiateReset(@ModelAttribute("password") Password password) {
        userRegistrationService.requestPasswordReset(password.getEmail());
        return "resetPassword2";
    }

    public static class PasswordReset {
        @Getter
        @Setter
        String token;

        @Getter
        @Setter
        @NotNull
        @NotBlank(message = "Password cannot be empty")
        @Size(min=5, message = "Password must be at least 5 characters")
        String password;
    }

    @GetMapping("/reset_password3")
    public String resetPasswordVerifyToken(WebRequest request, Model model, @RequestParam("token") String token) {

        VerificationToken verificationToken = userRegistrationService.getVerificationToken(token);
        if (verificationToken == null) {
            throw new ExceptionHandling.UnnamedException("Invalid Token", "Please try again, the token is not valid.");
        }

        Users user = verificationToken.getUser();
        Calendar cal = Calendar.getInstance();
        if ((verificationToken.getExpiryDate().getTime() - cal.getTime().getTime()) <= 0) {
            userRegistrationService.deleteToken(user);
            throw new ExceptionHandling.UnnamedException("Message Expired", "Please try to reset the password again..");
        }

        PasswordReset pw = new PasswordReset();
        pw.setToken(token);
        model.addAttribute("password", pw);

        return "resetPassword3";
    }

    @PostMapping("/reset_password3")
    public String registerUserAccount(
            @ModelAttribute("password") @Valid PasswordReset password, BindingResult bindingResult) {

        if (bindingResult.hasErrors())
            return "registration";

        userRegistrationService.resetPassword(password.getToken(), password.getPassword());
        return "resetPasswordSuccess";
    }
}

