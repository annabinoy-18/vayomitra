package com.vayomitra.security;

import java.util.UUID;

public record AuthUser(UUID userId, UUID sessionId, String email, String role, String accountStatus) {
    public boolean isElder() {
        return "ELDER".equals(role);
    }

    public boolean isVolunteer() {
        return "VOLUNTEER".equals(role);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isActive() {
        return "ACTIVE".equals(accountStatus);
    }
}
