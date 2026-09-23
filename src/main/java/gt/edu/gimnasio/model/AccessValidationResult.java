package gt.edu.gimnasio.model;

/** Representa el resultado de validar un código al solicitar acceso. */
public class AccessValidationResult {

    private final boolean authorized;
    private final String reason;
    private final AccessValidationData validationData;

    private AccessValidationResult(boolean authorized, String reason, AccessValidationData validationData) {
        this.authorized = authorized;
        this.reason = reason;
        this.validationData = validationData;
    }

    public static AccessValidationResult authorized(AccessValidationData validationData) {
        return new AccessValidationResult(true,
                "Acceso autorizado para " + validationData.getMemberName() + ".", validationData);
    }

    public static AccessValidationResult rejected(String reason, AccessValidationData validationData) {
        return new AccessValidationResult(false, reason, validationData);
    }

    public boolean isAuthorized() {
        return authorized;
    }

    public String getReason() {
        return reason;
    }

    public AccessValidationData getValidationData() {
        return validationData;
    }
}
