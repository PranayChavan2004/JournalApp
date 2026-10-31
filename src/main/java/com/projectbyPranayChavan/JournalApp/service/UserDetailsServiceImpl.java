package com.projectbyPranayChavan.JournalApp.service;

import com.projectbyPranayChavan.JournalApp.entities.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserService userService;

    public UserDetailsServiceImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        User user = userService.findByLogin(login);
        if (user == null) {
            throw new UsernameNotFoundException("Bad credentials");
        }
        return toUserDetails(user, user.getPassword());
    }

    public static UserDetails toUserDetails(User user) {
        return toUserDetails(user, "");
    }

    private static UserDetails toUserDetails(User user, String password) {
        List<String> roles = user.getRoles() == null ? List.of() : user.getRoles();
        List<GrantedAuthority> authorities = roles.stream()
                .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return new org.springframework.security.core.userdetails.User(user.getUsername(), password, authorities);
    }
}
