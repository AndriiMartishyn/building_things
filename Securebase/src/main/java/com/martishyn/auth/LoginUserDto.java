package com.martishyn.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginUserDto(
        @JsonProperty("email")
        String email,
        @JsonProperty("password")
        String password
){
}
