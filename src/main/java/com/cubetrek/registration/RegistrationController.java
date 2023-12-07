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

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

@Controller
public class RegistrationController {
    @Autowired
    private UserRegistrationService userRegistrationService;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    NewsletterSignupRepository newsletterSignupRepository;

    @Value("${cloudflare.turnstyle.secret}")
    String cloudflareTurnstyleSecret;

    Logger logger = LoggerFactory.getLogger(RegistrationController.class);

    @GetMapping("/registration")
    public String showRegistrationForm(WebRequest request, Model model) {
        UserDto userDto = new UserDto();
        model.addAttribute("user", userDto);
        return "registration";
    }

    @PostMapping("/registration")
    public String registerUserAccount(
            @ModelAttribute("user") @Valid UserDto userDto, BindingResult bindingResult, @RequestParam(name="cf-turnstile-response", required = false, defaultValue = "none") String cf_turnstile_response, HttpServletRequest request) {

        if (bindingResult.hasErrors())
            return "registration";

        try {
            if (cf_turnstile_response.equals("none")) {
                logger.error("Error Registration: no Cloudflare Turnstile transferred for Username: "+userDto.getName()+", email "+userDto.getEmail()+ ", IP "+request.getHeader("X-FORWARDED-FOR")); //"X-FORWARDED-FOR" contains the originating IP from NGINX
                throw new ExceptionHandling.UnnamedException("Something went wrong :(", "Could not finalize Registration, you might be a bot. Did you click the Human Verification button?");
            }
            HttpResponse<String> response = verifyCloudflareTurnstile(cf_turnstile_response, request.getHeader("X-FORWARDED-FOR"));
            if (response.statusCode()!=200) {
                logger.error("Error Registration: Cloudflare Turnstile returns not 200: "+response.statusCode()+"; "+response.body());
                throw new ExceptionHandling.UnnamedException("Something went wrong :(", "Could not finalize Registration, please try again later or send an email to contact@cubetrek.com");
            }
            boolean turnstile_success = (new ObjectMapper()).readTree(response.body()).get("success").asBoolean(false);
            if (!turnstile_success) {
                logger.error("Error Registration: Cloudflare Turnstile returns not true: "+response.body());
                throw new ExceptionHandling.UnnamedException("Something went wrong :(", "Could not finalize Registration, please try again later or send an email to contact@cubetrek.com");
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }


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

    public HttpResponse<String> verifyCloudflareTurnstile(String cf_turnstyle_response, String remoteip) throws URISyntaxException, IOException, InterruptedException {
        //See https://developers.cloudflare.com/turnstile/get-started/server-side-validation/
        HttpClient httpClient = HttpClient.newHttpClient();

        Map<String, String> content = new HashMap<>();
        content.put("secret", cloudflareTurnstyleSecret);
        content.put("response", cf_turnstyle_response);
        content.put("remoteip", remoteip);

        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .uri(new URI("https://challenges.cloudflare.com/turnstile/v0/siteverify"))
                .version(HttpClient.Version.HTTP_1_1)
                .POST(PolarAccesslinkService.getFormDataAsString(content))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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

