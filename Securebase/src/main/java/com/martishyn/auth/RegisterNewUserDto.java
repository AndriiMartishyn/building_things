package com.martishyn.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegisterNewUserDto(
        @JsonProperty("email")
        String email,
        @JsonProperty("password")
        String password
) {
}
