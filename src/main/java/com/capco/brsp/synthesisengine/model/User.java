package com.capco.brsp.synthesisengine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
public class User {

    private Long id;
    private String username;
    private String role;
    private String password;

    public User() {
    }

    public Set<User> setOfUsers() {
        Set<User> setOfUsers = Set.of(
                new User(1L, System.getenv("USER_1_USERNAME"), System.getenv("USER_1_ROLE"), System.getenv("USER_1_PASSWORD")),
                new User(2L, System.getenv("USER_2_USERNAME"), System.getenv("USER_2_ROLE"), System.getenv("USER_2_PASSWORD"))
        );

        // Remove users with null username to avoid NullPointerException
        return setOfUsers.stream()
                .filter(u -> u.getUsername() != null)
                .collect(java.util.stream.Collectors.toSet());
    }
}
