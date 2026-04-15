package com.martishyn.auth;

import com.martishyn.auth.db.User;
import com.martishyn.auth.db.UserAuthRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Service
public class DaoUserDetailsService implements UserDetailsService{

    private UserAuthRepository userAuthRepository;

    public DaoUserDetailsService(UserAuthRepository userAuthRepository) {
        this.userAuthRepository = userAuthRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String userEmail) throws UsernameNotFoundException {
        User userByEmail = userAuthRepository.findOptionalUserByEmail(userEmail)
                .orElseThrow( () -> new UsernameNotFoundException("user not found"));
        return new org.springframework.security.core.userdetails.User(
                userByEmail.getEmail(),
                userByEmail.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
