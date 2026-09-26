package gt.edu.gimnasio.service;

import java.sql.SQLException;
import java.util.Objects;

import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.repository.GymRepository;

/** Mantiene el gimnasio con el que trabaja esta instancia de la aplicación. */
public class GymContext {

    private static final String INITIAL_GYM_NAME = "Gimnasio Principal";

    private final GymRepository gymRepository;
    private Gym currentGym;

    public GymContext() {
        this(new GymRepository());
    }

    public GymContext(GymRepository gymRepository) {
        this.gymRepository = Objects.requireNonNull(gymRepository);
    }

    /** Obtiene el gimnasio inicial de la base de datos una sola vez. */
    public Gym getCurrentGym() throws SQLException {
        if (currentGym == null) {
            Gym gym = gymRepository.findByName(INITIAL_GYM_NAME)
                    .orElseThrow(() -> new IllegalStateException(
                            "No se encontró el gimnasio inicial en PostgreSQL."));

            if (!gym.isActive()) {
                throw new IllegalStateException(
                        "El gimnasio inicial está inactivo en PostgreSQL.");
            }

            currentGym = gym;
        }

        return currentGym;
    }

    public int getCurrentGymId() throws SQLException {
        return getCurrentGym().getId();
    }
}
