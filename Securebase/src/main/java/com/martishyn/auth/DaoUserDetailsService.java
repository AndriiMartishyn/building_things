package com.martishyn.auth;

import com.martishyn.auth.db.User;
import com.martishyn.auth.db.UserAuthRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

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
        List<GrantedAuthority> grantedAuthorities = new LinkedList<>();
        userByEmail.getRoles().forEach(role -> {
        grantedAuthorities.add(new SimpleGrantedAuthority(role.getRoleName()));});
        return new org.springframework.security.core.userdetails.User(
                userByEmail.getEmail(),
                userByEmail.getPassword(),
                grantedAuthorities);
    }
}
