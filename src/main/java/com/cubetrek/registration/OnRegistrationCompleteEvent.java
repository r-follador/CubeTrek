package com.cubetrek.registration;

import com.cubetrek.database.Users;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

import java.util.Locale;

@Getter
@Setter
public class OnRegistrationCompleteEvent extends ApplicationEvent {
    private Users user;

    public OnRegistrationCompleteEvent(Users user) {
        super(user);
        this.user = user;
    }
}
