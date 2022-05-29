package com.cubetrek.database;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import javax.persistence.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Entity(name = "users")
@Table(name = "users")
public class Users implements UserDetails {

    private static final long serialVersionUID = 1L;

    public enum UserRole {
        ADMIN, USER; //Use ROLE_ prefix for Spring Security
    }

    public enum UserTier {
        FREE, PAID;
    }

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @Column(name = "name", nullable = false)
    private String name;

    @Getter
    @Setter
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Getter
    @Setter
    @Column(name = "password", nullable = false)
    private String password;

    @Setter
    @Column(name = "userRole", nullable = false)
    private UserRole userRole = UserRole.USER;

    @Getter
    @Setter
    @Column(name = "userTier", nullable = false)
    private UserTier userTier = UserTier.FREE;

    @Override
    public String getUsername() {
        return name;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public List<String> getUserRole() {
        return Arrays.asList(userRole.toString());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        final SimpleGrantedAuthority simpleGrantedAuthority = new SimpleGrantedAuthority(userRole.name());
        return Collections.singletonList(simpleGrantedAuthority);
    }

    public UserDetails getUserdetails() {
        return User.withUsername(getName()).password(getPassword()).roles(userRole.toString()).disabled(!isEnabled()).accountExpired(!isAccountNonExpired()).credentialsExpired(!isCredentialsNonExpired()).accountLocked(!isAccountNonLocked()).build();
    }

    @Override
    public String toString() {
        return "Users{" +
                "name=" + name + '\'' +
                "email=" + email + '\'' +
                "password=" + password + '\'' +
                '}';
    }
}
