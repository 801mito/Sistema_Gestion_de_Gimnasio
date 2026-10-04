package gt.edu.gimnasio.service;

import java.util.Objects;

import gt.edu.gimnasio.model.Employee;

/** Identidad y gimnasio compartidos durante una sesión de la aplicación. */
public final class EmployeeSession {

    private final Employee employee;
    private final GymContext gymContext;

    public EmployeeSession(Employee employee) {
        this.employee = Objects.requireNonNull(employee);
        this.gymContext = new GymContext(employee);
    }

    public Employee getEmployee() {
        return employee;
    }

    public GymContext getGymContext() {
        return gymContext;
    }
}
