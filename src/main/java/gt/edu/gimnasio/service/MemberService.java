package gt.edu.gimnasio.service;

import java.sql.SQLException;

import gt.edu.gimnasio.model.Member;
import gt.edu.gimnasio.repository.MemberRepository;

/** Aplica las reglas de negocio para registrar miembros. */
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /** Valida y registra un miembro nuevo. */
    public Member createMember(String firstNames, String lastNames, String documentNumber,
                               String phone, String email) throws MemberValidationException, SQLException {
        String normalizedFirstNames = requireText(firstNames, "Los nombres son obligatorios.");
        String normalizedLastNames = requireText(lastNames, "Los apellidos son obligatorios.");
        String normalizedDocument = optionalText(documentNumber);
        String normalizedPhone = optionalText(phone);
        String normalizedEmail = optionalText(email);

        validateDocument(normalizedDocument);
        validatePhone(normalizedPhone);
        validateEmail(normalizedEmail);

        if (normalizedDocument != null && memberRepository.existsByDocument(normalizedDocument)) {
            throw new MemberValidationException("Ya existe un miembro con ese número de documento.");
        }

        return memberRepository.save(
                normalizedFirstNames, normalizedLastNames, normalizedDocument, normalizedPhone, normalizedEmail);
    }

    private String requireText(String value, String message) throws MemberValidationException {
        String normalizedValue = optionalText(value);

        if (normalizedValue == null) {
            throw new MemberValidationException(message);
        }

        return normalizedValue;
    }

    private String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private void validateDocument(String documentNumber) throws MemberValidationException {
        if (documentNumber != null && !documentNumber.matches("[A-Za-z0-9-]{4,30}")) {
            throw new MemberValidationException("El documento debe tener entre 4 y 30 caracteres alfanuméricos.");
        }
    }

    private void validatePhone(String phone) throws MemberValidationException {
        if (phone != null && !phone.matches("[0-9+() -]{7,25}")) {
            throw new MemberValidationException("El teléfono debe contener entre 7 y 25 caracteres válidos.");
        }
    }

    private void validateEmail(String email) throws MemberValidationException {
        if (email != null && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new MemberValidationException("El correo electrónico no tiene un formato válido.");
        }
    }
}
