package com.cubetrek.registration;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.Users;
import com.cubetrek.database.VerificationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Calendar;
import java.util.Locale;

@Controller
public class RegistrationController {
    @Autowired
    private UserRegistrationService userRegistrationService;

    @Autowired
    ApplicationEventPublisher eventPublisher;

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
        return "redirect:/successRegisterValidation";
    }

    @GetMapping("/successRegisterValidation")
    public String successRegistration() {
        return "successRegisterValidation";
    }
}
