package gt.edu.gimnasio.service;

/** Indica que no se puede asignar una membresía por una regla de negocio. */
public class MembershipValidationException extends Exception {

    public MembershipValidationException(String message) {
        super(message);
    }
}
