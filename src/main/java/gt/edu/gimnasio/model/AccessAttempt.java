package gt.edu.gimnasio.model;

import java.time.LocalDateTime;

/** Un resultado de validación conservado en el gimnasio donde ocurrió. */
public record AccessAttempt(int id, int gymId, Integer accessCodeId, String code,
                            String result, String reason, LocalDateTime occurredAt) { }
