package gt.edu.gimnasio.service;

import java.sql.SQLException;
import java.util.Objects;

import gt.edu.gimnasio.model.Employee;
import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.repository.GymRepository;

/** Mantiene el gimnasio con el que trabaja esta instancia de la aplicación. */
public class GymContext {

    private static final String INITIAL_GYM_NAME = "Gimnasio Principal";

    private final GymRepository gymRepository;
    private final Integer employeeGymId;
    private Gym currentGym;

    public GymContext() {
        this(new GymRepository());
    }

    public GymContext(GymRepository gymRepository) {
        this.gymRepository = Objects.requireNonNull(gymRepository);
        this.employeeGymId = null;
    }

    /** Prepara el contexto de un empleado ya autenticado; no acepta un ID elegido en la UI. */
    public GymContext(Employee employee) {
        this(new GymRepository(), employee);
    }

    GymContext(GymRepository gymRepository, Employee employee) {
        this.gymRepository = Objects.requireNonNull(gymRepository);
        Employee account = Objects.requireNonNull(employee);
        if (account.getGymId() <= 0 || !account.isActive() || !account.isGymActive()) {
            throw new IllegalArgumentException("La cuenta no tiene un gimnasio activo.");
        }
        this.employeeGymId = account.getGymId();
    }

    /** Obtiene el gimnasio inicial de la base de datos una sola vez. */
    public Gym getCurrentGym() throws SQLException {
        if (currentGym == null) {
            Gym gym = employeeGymId == null
                    ? gymRepository.findByName(INITIAL_GYM_NAME).orElseThrow(() ->
                            new IllegalStateException("No se encontró el gimnasio inicial en PostgreSQL."))
                    : gymRepository.findById(employeeGymId).orElseThrow(() ->
                            new IllegalStateException("No se encontró el gimnasio del empleado en PostgreSQL."));

            if (!gym.isActive()) {
                throw new IllegalStateException(
                        employeeGymId == null
                                ? "El gimnasio inicial está inactivo en PostgreSQL."
                                : "El gimnasio del empleado está inactivo en PostgreSQL.");
            }

            currentGym = gym;
        }

        return currentGym;
    }

    public int getCurrentGymId() throws SQLException {
        return getCurrentGym().getId();
    }
}
