package gt.edu.gimnasio.service;

/** Rechazo genérico: no revela si falló el usuario, la clave o el estado. */
public class AuthenticationException extends Exception {

    public AuthenticationException() {
        super("No fue posible iniciar sesión con esas credenciales.");
    }
}
