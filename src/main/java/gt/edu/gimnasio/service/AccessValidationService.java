package gt.edu.gimnasio.service;

import java.sql.SQLException;
import java.time.LocalDate;

import gt.edu.gimnasio.model.AccessValidationData;
import gt.edu.gimnasio.model.AccessValidationResult;
import gt.edu.gimnasio.repository.AccessCodeRepository;

/** Aplica las reglas de negocio para autorizar o rechazar un acceso. */
public class AccessValidationService {

    private final AccessCodeRepository accessCodeRepository;

    public AccessValidationService(AccessCodeRepository accessCodeRepository) {
        this.accessCodeRepository = accessCodeRepository;
    }

    /** Valida un código con el estado y vigencia de la membresía asociada. */
    public AccessValidationResult validate(String enteredCode) throws SQLException {
        String normalizedCode = enteredCode == null ? "" : enteredCode.trim();

        if (normalizedCode.isEmpty()) {
            return AccessValidationResult.rejected("Ingresa un código de acceso.", null);
        }

        AccessValidationData validationData = accessCodeRepository.findValidationDataByCode(normalizedCode);

        if (validationData == null) {
            return AccessValidationResult.rejected("El código ingresado no existe.", null);
        }

        if (!validationData.isAccessCodeActive()) {
            return AccessValidationResult.rejected("El código de acceso está inactivo.", validationData);
        }

        if (!"ACTIVA".equals(validationData.getMembershipStatus())) {
            return AccessValidationResult.rejected(
                    "La membresía está " + validationData.getMembershipStatus().toLowerCase() + ".",
                    validationData);
        }

        LocalDate currentDate = LocalDate.now();

        if (currentDate.isBefore(validationData.getMembershipStartDate())) {
            return AccessValidationResult.rejected("La membresía aún no inicia su vigencia.", validationData);
        }

        if (currentDate.isAfter(validationData.getMembershipEndDate())) {
            return AccessValidationResult.rejected("La membresía está vencida.", validationData);
        }

        return AccessValidationResult.authorized(validationData);
    }
}
