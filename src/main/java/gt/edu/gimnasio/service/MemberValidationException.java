package gt.edu.gimnasio.service;

/** Indica que los datos de un miembro no cumplen las reglas de negocio. */
public class MemberValidationException extends Exception {

    public MemberValidationException(String message) {
        super(message);
    }
}
