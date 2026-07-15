package com.martishyn.auth.jwt;

import java.util.StringTokenizer;

public record JwtPairDto(String accessToken, String refreshToken) {
}
