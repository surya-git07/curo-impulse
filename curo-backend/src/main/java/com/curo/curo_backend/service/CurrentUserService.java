package com.curo.curo_backend.service;

import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** The signed-in user, loaded from the id in the JWT. The role comes from the database, not the token. */
@Service
public class CurrentUserService {

    @Autowired
    private UserRepository userRepository;

    public AppUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken || auth.getName() == null) {
            throw unauthenticated();
        }
        AppUser user = userRepository.findById(auth.getName()).orElseThrow(CurrentUserService::unauthenticated);
        if (user.getDeletedAt() != null) {
            throw unauthenticated();
        }
        return user;
    }

    private static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in to continue.");
    }
}
