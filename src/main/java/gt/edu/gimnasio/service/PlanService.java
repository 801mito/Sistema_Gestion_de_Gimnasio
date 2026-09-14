package gt.edu.gimnasio.service;

import java.sql.SQLException;

import gt.edu.gimnasio.model.Plan;
import gt.edu.gimnasio.repository.PlanRepository;

/** Aplica las reglas de negocio antes de registrar planes de membresía. */
public class PlanService {

    private final PlanRepository planRepository;

    public PlanService() {
        this(new PlanRepository());
    }

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    /**
     * Valida y registra un nuevo plan activo.
     *
     * @param name nombre del plan.
     * @param durationDays duración del plan en días.
     * @return el plan creado.
     * @throws PlanValidationException si los datos no son válidos.
     * @throws SQLException si ocurre un error al acceder a PostgreSQL.
     */
    public Plan createPlan(String name, int durationDays) throws PlanValidationException, SQLException {
        String normalizedName = validateName(name);

        if (durationDays < 1) {
            throw new PlanValidationException("La duración debe ser de al menos un día.");
        }

        if (planRepository.existsByName(normalizedName)) {
            throw new PlanValidationException("Ya existe un plan con ese nombre.");
        }

        return planRepository.save(normalizedName, durationDays);
    }

    private String validateName(String name) throws PlanValidationException {
        if (name == null || name.isBlank()) {
            throw new PlanValidationException("El nombre del plan es obligatorio.");
        }

        return name.trim();
    }
}
