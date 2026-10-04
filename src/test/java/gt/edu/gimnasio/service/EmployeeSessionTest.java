package gt.edu.gimnasio.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import gt.edu.gimnasio.model.Employee;

class EmployeeSessionTest {

    @Test
    void keepsOneEmployeeAndGymContextForTheSession() {
        Employee employee = new Employee(9, 73, "Gimnasio Secundario", "jaime",
                true, true, LocalDateTime.now());

        EmployeeSession session = new EmployeeSession(employee);

        assertSame(employee, session.getEmployee());
        assertSame(session.getGymContext(), session.getGymContext());
    }

    @Test
    void rejectsInactiveEmployeeBeforeOpeningModules() {
        Employee inactive = new Employee(9, 73, "Gimnasio Secundario", "jaime",
                false, true, LocalDateTime.now());

        assertThrows(IllegalArgumentException.class, () -> new EmployeeSession(inactive));
        assertThrows(NullPointerException.class, () -> new EmployeeSession(null));
    }
}
