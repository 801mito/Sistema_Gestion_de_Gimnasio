package gt.edu.gimnasio.service;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import gt.edu.gimnasio.model.Employee;
import gt.edu.gimnasio.repository.EmployeeRepository;
import gt.edu.gimnasio.repository.EmployeeRepository.Credentials;

/** Verifica empleados; el gimnasio se obtiene de la cuenta, nunca del cliente. */
public class AuthenticationService {

    // Reduce la diferencia de tiempo entre un usuario inexistente y una clave errónea.
    private static final String DUMMY_HASH = createDummyHash();

    private final EmployeeRepository employeeRepository;
    private final PasswordHasher passwordHasher;

    public AuthenticationService() {
        this(new EmployeeRepository(), new PasswordHasher());
    }

    public AuthenticationService(EmployeeRepository employeeRepository, PasswordHasher passwordHasher) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
    }

    /**
     * Devuelve la identidad y el único gimnasio de una cuenta disponible.
     * Quien llama conserva la propiedad de password y debe limpiarlo al terminar.
     */
    public Employee authenticate(String username, char[] password)
            throws AuthenticationException, SQLException {
        Optional<Credentials> account;
        try {
            account = employeeRepository.findByUsername(username);
        } catch (IllegalArgumentException invalidUsername) {
            account = Optional.empty();
        }

        String encoded = account.map(Credentials::getPasswordHash).orElse(DUMMY_HASH);
        char[] candidate = password == null ? new char[] {'\0'} : password;
        boolean passwordMatches = passwordHasher.matches(candidate, encoded);

        if (account.isEmpty() || password == null || !passwordMatches
                || !account.get().getEmployee().isActive()
                || !account.get().getEmployee().isGymActive()) {
            throw new AuthenticationException();
        }

        return account.get().getEmployee();
    }

    private static String createDummyHash() {
        char[] dummy = new char[32];
        Arrays.fill(dummy, '#');
        try {
            return new PasswordHasher().hash(dummy);
        } finally {
            Arrays.fill(dummy, '\0');
        }
    }
}
