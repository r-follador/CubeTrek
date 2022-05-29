package com.cubetrek.registration;

import com.cubetrek.database.Users;
import com.cubetrek.database.UsersRepository;
import com.cubetrek.ExceptionHandling;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
@Transactional
public class UserRegistrationService {

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Users register(UserDto user) {

        System.out.println("new user....");
        //Let's check if user already registered with us
        if(checkIfUserExist(user.getEmail()))
            throw new ExceptionHandling.UserRegistrationException("User already exists for this email");

        if (!user.getPassword().equals(user.getMatchingPassword()))
            throw new ExceptionHandling.UserRegistrationException("Passwords not matching");

        Users userField = new Users();
        userField.setName(user.getName());
        userField.setEmail(user.getEmail());
        userField.setPassword(passwordEncoder.encode(user.getPassword()));
        userField.setUserTier(Users.UserTier.FREE);
        userField.setUserRole(Users.UserRole.USER);
        return usersRepository.save(userField);
    }

    public boolean checkIfUserExist(String email) {
        return usersRepository.findByEmail(email).isPresent();
    }

    @Bean
    public PasswordEncoder getEncoder() {
        return new BCryptPasswordEncoder();
    }
}