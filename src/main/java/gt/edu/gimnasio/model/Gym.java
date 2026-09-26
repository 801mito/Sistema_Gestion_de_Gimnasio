package gt.edu.gimnasio.model;

import java.time.LocalDateTime;

/** Representa un gimnasio al que pertenecen los datos de la aplicación. */
public class Gym {

    private final int id;
    private final String name;
    private final boolean active;
    private final LocalDateTime createdAt;

    public Gym(int id, String name, boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
