package com.cubetrek;

import com.cubetrek.registration.CustomAuthenticationSuccessHandler;
import com.cubetrek.registration.MyUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity(debug = false)
public class SecurityConfig {

    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    private final PasswordEncoder passwordEncoder;
    private final MyUserDetailsService userDetailsService;

    public SecurityConfig(CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
                          PasswordEncoder passwordEncoder,
                          MyUserDetailsService userDetailsService) {
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authorizeRequests(authorize -> authorize
                        .requestMatchers("/",
                                "/css/**",
                                "/assets/**",
                                "/js/**",
                                "/static/**",
                                "/home",
                                "/reset_password3*",
                                "/reset_password",
                                "/registration*",
                                "registrationConfirm*",
                                "/successRegister*",
                                "/successRegisterValidation*",
                                "/upload_anonymous",
                                "/newslettersignup/**",
                                "/api/**",
                                "/view/**",
                                "/view2d/**",
                                "/replay/**",
                                "/garminconnect",
                                "/polarconnect",
                                "/suuntoconnect",
                                "/corosconnect",
                                "/corosconnect/status",
                                "/stripe_hook").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .successHandler(customAuthenticationSuccessHandler)
                        .permitAll()
                )
                .logout(logout -> logout.permitAll());

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }
}
