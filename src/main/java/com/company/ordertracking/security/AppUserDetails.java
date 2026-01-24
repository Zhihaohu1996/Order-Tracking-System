package com.company.ordertracking.security;

import com.company.ordertracking.entity.AppUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AppUserDetails implements UserDetails {

    private final AppUser user;

    public AppUserDetails(AppUser user) {
        this.user = user;
    }

    public AppUser getUser() {
        return user;
    }

    @Override
    public Collection<SimpleGrantedAuthority> getAuthorities() {
        String role = user.getRole() == null ? "GUEST" : user.getRole().trim().toUpperCase();
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
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
        return Boolean.TRUE.equals(user.getEnabled());
    }
}
