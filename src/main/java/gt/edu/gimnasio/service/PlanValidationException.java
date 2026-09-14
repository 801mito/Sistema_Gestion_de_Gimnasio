package gt.edu.gimnasio.service;

/** Indica que los datos ingresados para un plan no cumplen una regla de negocio. */
public class PlanValidationException extends Exception {

    public PlanValidationException(String message) {
        super(message);
    }
}
