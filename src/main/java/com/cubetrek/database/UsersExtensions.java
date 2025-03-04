package com.cubetrek.database;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Optional;

@Getter
@Setter
@Entity(name = "usersextensions")
@Table(name = "usersextensions")
public class UsersExtensions implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Integer maximumHeartrate;

    public Optional<Integer> getMaximumHeartRate() {
        return Optional.ofNullable(this.maximumHeartrate);
    }
}
