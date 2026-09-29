package gt.edu.gimnasio.repository;

import java.sql.SQLException;

/** Indica que el plan solicitado no está disponible dentro del gimnasio actual. */
public class PlanNotFoundException extends SQLException {

    private static final long serialVersionUID = 1L;

    public PlanNotFoundException() {
        super("No se encontró el plan en el gimnasio actual.");
    }
}
