package gt.edu.gimnasio.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;

import gt.edu.gimnasio.model.AccessValidationData;
import gt.edu.gimnasio.model.AccessValidationResult;
import gt.edu.gimnasio.repository.AccessAttemptRepository;
import gt.edu.gimnasio.repository.AccessCodeRepository;

/** Aplica las reglas de negocio para autorizar o rechazar un acceso. */
public class AccessValidationService {

    private final AccessCodeRepository accessCodeRepository;
    private final AccessAttemptRepository accessAttemptRepository;

    public AccessValidationService(AccessCodeRepository accessCodeRepository,
                                   AccessAttemptRepository accessAttemptRepository) {
        this.accessCodeRepository = Objects.requireNonNull(accessCodeRepository);
        this.accessAttemptRepository = Objects.requireNonNull(accessAttemptRepository);
    }

    /** Valida un código con el estado y vigencia de la membresía asociada. */
    public AccessValidationResult validate(String enteredCode) throws SQLException {
        String normalizedCode = enteredCode == null ? "" : enteredCode.trim();

        AccessValidationData validationData = normalizedCode.isEmpty()
                ? null
                : accessCodeRepository.findValidationDataByCode(normalizedCode);

        AccessValidationResult result;
        if (normalizedCode.isEmpty()) {
            result = AccessValidationResult.rejected("Ingresa un código de acceso.", null);
        } else if (validationData == null) {
            result = AccessValidationResult.rejected("El código ingresado no existe.", null);
        } else if (!validationData.isAccessCodeActive()) {
            result = AccessValidationResult.rejected("El código de acceso está inactivo.", validationData);
        } else if (!"ACTIVA".equals(validationData.getMembershipStatus())) {
            result = AccessValidationResult.rejected(
                    "La membresía está " + validationData.getMembershipStatus().toLowerCase() + ".",
                    validationData);
        } else {
            LocalDate currentDate = LocalDate.now();
            if (currentDate.isBefore(validationData.getMembershipStartDate())) {
                result = AccessValidationResult.rejected("La membresía aún no inicia su vigencia.", validationData);
            } else if (currentDate.isAfter(validationData.getMembershipEndDate())) {
                result = AccessValidationResult.rejected("La membresía está vencida.", validationData);
            } else {
                result = AccessValidationResult.authorized(validationData);
            }
        }

        Integer accessCodeId = validationData == null ? null : validationData.getAccessCodeId();
        String storedReason = result.isAuthorized() ? "Acceso autorizado." : result.getReason();
        if (storedReason.length() > 100) {
            storedReason = storedReason.substring(0, 100);
        }
        accessAttemptRepository.save(accessCodeId, result.isAuthorized(), storedReason);
        return result;
    }
}
