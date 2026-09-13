package gt.edu.gimnasio.model;

import java.time.LocalDateTime;

/** Representa un plan de membresía disponible en el gimnasio. */
public class Plan {

    private final int id;
    private final String name;
    private final int durationDays;
    private final boolean active;
    private final LocalDateTime createdAt;

    public Plan(int id, String name, int durationDays, boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.durationDays = durationDays;
        this.active = active;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
