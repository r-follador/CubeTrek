package com.cubetrek.registration;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.NewsletterSignup;
import com.cubetrek.database.NewsletterSignupRepository;
import com.cubetrek.database.Users;
import com.cubetrek.database.VerificationToken;
import com.cubetrek.upload.polaraccesslink.PolarAccesslinkService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
            @ModelAttribute("user") @Valid UserDto userDto, BindingResult bindingResult, HttpServletRequest request) {

        if (bindingResult.hasErrors()) {
            return "registration";
        }

        try {
            // Disabling Cloudflare Turnstile verification
            // Commenting out Turnstile verification logic
            // String cf_turnstile_response = request.getParameter("cf-turnstile-response");
            // if (cf_turnstile_response.equals("none")) {
            //     logger.error("Error Registration: no Cloudflare Turnstile transferred for Username: " + userDto.getName() + ", email " + userDto.getEmail() + ", IP " + request.getHeader("X-FORWARDED-FOR"));
            //     throw new ExceptionHandling.UnnamedException("Something went wrong :(", "Could not finalize Registration, you might be a bot. Did you click the Human Verification button?");
            // }
            // HttpResponse<String> response = verifyCloudflareTurnstile(cf_turnstile_response, request.getHeader("X-FORWARDED-FOR"));
            // if (response.statusCode() != 200) {
            //     logger.error("Error Registration: Cloudflare Turnstile returns not 200: " + response.statusCode() + "; " + response.body());
            //     throw new ExceptionHandling.UnnamedException("Something went wrong :(", "Could not finalize Registration, please try again later or send an email to contact@cubetrek.com");
            // }
            // boolean turnstile_success = (new ObjectMapper()).readTree(response.body()).get("success").asBoolean(false);
            // if (!turnstile_success) {
            //     logger.error("Error Registration: Cloudflare Turnstile returns not true: " + response.body());
            //     throw new ExceptionHandling.UnnamedException("Something went wrong :(", "Could not finalize Registration, please try again later or send an email to contact@cubetrek.com");
            // }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            // Register user without email verification
            Users registered = userRegistrationService.register(userDto);
            registered.setEnabled(true); // Directly enabling the user without email verification
            userRegistrationService.saveRegisteredUser(registered); // Ensure the user is saved

            // Optionally remove email verification event if not needed
            // eventPublisher.publishEvent(new OnRegistrationCompleteEvent(registered));

        } catch (ExceptionHandling.UserRegistrationException ex) { // the email address exists already
            bindingResult.addError(new FieldError("user", "email", ex.msg));
            return "registration";
        } catch (RuntimeException ex) {
            logger.error("Registration Error", ex);
            throw new ExceptionHandling.UnnamedException("Something went wrong :(", "Could not finalize Registration, please try again later or send an email to contact@cubetrek.com");
        }

        return "successRegister"; // Registration success page
    }

    // Removed the verifyCloudflareTurnstile method since Turnstile verification is disabled

    @GetMapping("/registrationConfirm")
    public String confirmRegistration(WebRequest request, Model model, @RequestParam("token") String token) {
        // Removed this method since email verification is disabled
        throw new UnsupportedOperationException("Email verification is disabled.");
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
        @Size(min = 5, message = "Password must be at least 5 characters")
        String password;
    }

    @GetMapping("/reset_password3")
    public String resetPasswordVerifyToken(WebRequest request, Model model, @RequestParam("token") String token) {
        // Logic for resetting password, keep this as is
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
