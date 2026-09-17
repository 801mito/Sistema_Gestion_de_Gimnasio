package gt.edu.gimnasio.service;

import java.sql.SQLException;
import java.sql.Connection;
import java.time.LocalDate;

import gt.edu.gimnasio.config.DatabaseConnection;
import gt.edu.gimnasio.model.Member;
import gt.edu.gimnasio.model.Plan;
import gt.edu.gimnasio.repository.AccessCodeRepository;
import gt.edu.gimnasio.repository.MembershipRepository;

/** Aplica las reglas de negocio al asignar membresías. */
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final AccessCodeRepository accessCodeRepository;
    private final AccessCodeGenerator accessCodeGenerator;

    public MembershipService(MembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
        accessCodeRepository = new AccessCodeRepository();
        accessCodeGenerator = new AccessCodeGenerator(accessCodeRepository);
    }

    /** Calcula la fecha final inclusiva a partir de la duración del plan. */
    public LocalDate calculateEndDate(Plan plan, LocalDate startDate) {
        return startDate.plusDays(plan.getDurationDays() - 1L);
    }

    /** Valida y asigna una membresía activa a un miembro. */
    public String assignMembership(Member member, Plan plan, LocalDate startDate)
            throws MembershipValidationException, SQLException {
        if (member == null) {
            throw new MembershipValidationException("Selecciona un miembro.");
        }

        if (plan == null) {
            throw new MembershipValidationException("Selecciona un plan activo.");
        }

        if (startDate == null) {
            throw new MembershipValidationException("Selecciona una fecha de inicio.");
        }

        if (!plan.isActive()) {
            throw new MembershipValidationException("El plan seleccionado no está activo.");
        }

        if (membershipRepository.hasActiveMembership(member.getId())) {
            throw new MembershipValidationException("El miembro ya tiene una membresía activa.");
        }

        try (Connection connection = DatabaseConnection.openConnection()) {
            connection.setAutoCommit(false);

            try {
                int membershipId = membershipRepository.save(
                        connection, member.getId(), plan.getId(), startDate, calculateEndDate(plan, startDate));
                String accessCode = accessCodeGenerator.generateUniqueCode(connection);
                accessCodeRepository.save(connection, membershipId, accessCode);
                connection.commit();
                return accessCode;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}
