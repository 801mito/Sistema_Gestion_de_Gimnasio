package gt.edu.gimnasio.repository;

import java.sql.SQLException;

/** Indica que el miembro solicitado no está disponible dentro del gimnasio actual. */
public class MemberNotFoundException extends SQLException {

    private static final long serialVersionUID = 1L;

    public MemberNotFoundException() {
        super("No se encontró el miembro en el gimnasio actual.");
    }
}
