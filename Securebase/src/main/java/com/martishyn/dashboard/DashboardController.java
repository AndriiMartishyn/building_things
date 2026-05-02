package com.martishyn.dashboard;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("api/v1/dashboard")
public class DashboardController {

    @PreAuthorize("hasRole('ROLE_CUSTOMER')")
    @GetMapping("/roles")
    public ResponseEntity<?> getUserGrantedAuthoritiesInformation(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final UserDetails principal = (UserDetails) authentication.getPrincipal();
        final String roles = principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));
        return ResponseEntity.ok(roles);
    }
 }
