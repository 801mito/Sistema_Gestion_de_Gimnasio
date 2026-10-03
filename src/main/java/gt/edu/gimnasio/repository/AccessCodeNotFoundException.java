package gt.edu.gimnasio.repository;

import java.sql.SQLException;

/** Indica que el código solicitado no está disponible en el gimnasio actual. */
public class AccessCodeNotFoundException extends SQLException {

    private static final long serialVersionUID = 1L;

    public AccessCodeNotFoundException() {
        super("No se encontró el código de acceso en el gimnasio actual.");
    }
}
