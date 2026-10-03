package gt.edu.gimnasio.model;

import java.time.LocalDateTime;

/** Cuenta de acceso de un empleado; no representa a un miembro del gimnasio. */
public class Employee {

    private final int id;
    private final int gymId;
    private final String gymName;
    private final String username;
    private final boolean active;
    private final boolean gymActive;
    private final LocalDateTime createdAt;

    public Employee(int id, int gymId, String gymName, String username,
                    boolean active, boolean gymActive, LocalDateTime createdAt) {
        this.id = id;
        this.gymId = gymId;
        this.gymName = gymName;
        this.username = username;
        this.active = active;
        this.gymActive = gymActive;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getGymId() {
        return gymId;
    }

    public String getGymName() {
        return gymName;
    }

    public String getUsername() {
        return username;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isGymActive() {
        return gymActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
