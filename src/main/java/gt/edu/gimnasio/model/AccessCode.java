package gt.edu.gimnasio.model;

import java.time.LocalDateTime;

/** Representa un código de acceso asociado a una membresía. */
public class AccessCode {

    private final int id;
    private final int membershipId;
    private final String code;
    private final boolean active;
    private final LocalDateTime createdAt;

    public AccessCode(int id, int membershipId, String code, boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.membershipId = membershipId;
        this.code = code;
        this.active = active;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getMembershipId() {
        return membershipId;
    }

    public String getCode() {
        return code;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
