package gt.edu.gimnasio.service;

import java.sql.SQLException;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.Objects;

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
        this.membershipRepository = Objects.requireNonNull(membershipRepository);
        accessCodeRepository = new AccessCodeRepository(membershipRepository.getGymContext());
        accessCodeGenerator = new AccessCodeGenerator(accessCodeRepository);
    }

    /** Calcula la fecha final inclusiva a partir de la duración del plan. */
    public LocalDate calculateEndDate(Plan plan, LocalDate startDate) {
        return startDate.plusDays(plan.getDurationDays() - 1L);
    }

    public void freeze(int membershipId) throws SQLException, MembershipValidationException {
        transition(membershipId, "ACTIVA", "CONGELADA");
    }

    public void reactivate(int membershipId) throws SQLException, MembershipValidationException {
        transition(membershipId, "CONGELADA", "ACTIVA");
    }

    private void transition(int membershipId, String expected, String next)
            throws SQLException, MembershipValidationException {
        int gymId = membershipRepository.currentGymId();
        try (Connection connection = DatabaseConnection.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!membershipRepository.isGymActive(connection, gymId)) {
                    throw new MembershipValidationException("El gimnasio actual ya no está disponible.");
                }
                int memberId = membershipRepository.memberIdForStatus(connection, gymId, membershipId);
                if (memberId == 0 || !membershipRepository.memberExists(connection, gymId, memberId)) {
                    throw new MembershipValidationException("No se encontró la membresía en el gimnasio actual.");
                }
                MembershipRepository.StatusData state = membershipRepository.lockStatus(connection, gymId, membershipId);
                if (state == null || state.memberId() != memberId) {
                    throw new MembershipValidationException("No se encontró la membresía en el gimnasio actual.");
                }
                if (!expected.equals(state.status())) {
                    throw new MembershipValidationException("La membresía ya no está en el estado requerido.");
                }
                if ("ACTIVA".equals(next)) {
                    if (LocalDate.now().isAfter(state.endDate())) {
                        throw new MembershipValidationException("No se puede reactivar una membresía cuya fecha final ya pasó.");
                    }
                    if (membershipRepository.hasActiveMembership(connection, gymId, memberId)) {
                        throw new MembershipValidationException("El miembro ya tiene otra membresía activa.");
                    }
                }
                if (!membershipRepository.changeStatus(connection, gymId, membershipId, expected, next)) {
                    throw new MembershipValidationException("La membresía cambió mientras se procesaba la solicitud.");
                }
                connection.commit();
            } catch (SQLException | MembershipValidationException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        }
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

        int gymId = membershipRepository.currentGymId();
        try (Connection connection = DatabaseConnection.openConnection()) {
            connection.setAutoCommit(false);

            try {
                if (!membershipRepository.isGymActive(connection, gymId)) {
                    throw new MembershipValidationException("El gimnasio actual ya no está disponible.");
                }
                if (!membershipRepository.memberExists(connection, gymId, member.getId())) {
                    throw new MembershipValidationException("No se encontró el miembro en el gimnasio actual.");
                }
                int durationDays = membershipRepository.activePlanDurationDays(connection, gymId, plan.getId());
                if (durationDays < 1) {
                    throw new MembershipValidationException("No se encontró un plan activo en el gimnasio actual.");
                }
                if (membershipRepository.hasActiveMembership(connection, gymId, member.getId())) {
                    throw new MembershipValidationException("El miembro ya tiene una membresía activa.");
                }

                int membershipId = membershipRepository.save(
                        connection, gymId, member.getId(), plan.getId(), startDate,
                        startDate.plusDays(durationDays - 1L));
                String accessCode = accessCodeGenerator.generateUniqueCode(connection);
                accessCodeRepository.save(connection, membershipId, accessCode);
                connection.commit();
                return accessCode;
            } catch (SQLException | MembershipValidationException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        }
    }
}
