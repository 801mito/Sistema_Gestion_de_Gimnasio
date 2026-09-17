package gt.edu.gimnasio.service;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;

import gt.edu.gimnasio.repository.AccessCodeRepository;

/** Genera códigos de acceso legibles y únicos dentro del sistema. */
public class AccessCodeGenerator {

    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_PART_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final AccessCodeRepository accessCodeRepository;

    public AccessCodeGenerator(AccessCodeRepository accessCodeRepository) {
        this.accessCodeRepository = accessCodeRepository;
    }

    /** Genera un valor sin espacios y verifica que no esté registrado. */
    public String generateUniqueCode(Connection connection) throws SQLException {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = createCandidate();

            if (!accessCodeRepository.existsByCode(connection, code)) {
                return code;
            }
        }

        throw new SQLException("No fue posible generar un código de acceso único.");
    }

    private String createCandidate() {
        StringBuilder code = new StringBuilder("GYM-");

        for (int index = 0; index < RANDOM_PART_LENGTH; index++) {
            code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }

        return code.toString();
    }
}
