package gt.edu.gimnasio.app;

import java.io.Console;
import java.sql.SQLException;
import java.util.Arrays;

import gt.edu.gimnasio.model.Gym;
import gt.edu.gimnasio.repository.EmployeeRepository;
import gt.edu.gimnasio.repository.GymRepository;

/** Aprovisiona localmente la primera cuenta de un gimnasio, fuera de la UI. */
public final class EmployeeBootstrap {

    private EmployeeBootstrap() {
    }

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null || args.length != 0) {
            System.err.println("Ejecuta esta herramienta en una terminal interactiva y sin argumentos.");
            return;
        }

        String url = System.getenv("DB_URL");
        if (url == null || url.isBlank()) {
            System.err.println("Configura DB_URL, DB_USER y DB_PASSWORD antes de continuar.");
            return;
        }
        // No mostrar parámetros de la URL: podrían contener credenciales.
        String databaseTarget = url.split("\\?", 2)[0]
                .replaceFirst("(?<=//)[^/]*@", "***@");
        console.printf("Base de datos: %s%n", databaseTarget);
        String gymName = console.readLine("Nombre exacto del gimnasio existente: ");
        if (gymName == null || gymName.isBlank()) {
            console.printf("Operación cancelada.%n");
            return;
        }

        try {
            Gym gym = new GymRepository().findByName(gymName.trim())
                    .orElseThrow(() -> new IllegalArgumentException("No existe ese gimnasio."));
            if (!gym.isActive()) {
                throw new IllegalArgumentException("El gimnasio está inactivo.");
            }
            String username = console.readLine("Usuario del primer empleado: ");
            if (username == null) {
                console.printf("Operación cancelada.%n");
                return;
            }
            char[] password = console.readPassword("Contraseña nueva: ");
            char[] confirmation = console.readPassword("Repite la contraseña: ");
            try {
                if (password == null || confirmation == null || password.length < 12
                        || !Arrays.equals(password, confirmation)) {
                    throw new IllegalArgumentException(
                            "Las contraseñas deben coincidir y tener al menos 12 caracteres.");
                }
                String approval = console.readLine("Escribe CREAR para confirmar: ");
                if (!"CREAR".equals(approval)) {
                    console.printf("Operación cancelada.%n");
                    return;
                }
                int employeeId = new EmployeeRepository().createFirstForGym(gym.getId(), username, password);
                console.printf("Empleado creado con ID %d para %s.%n", employeeId, gym.getName());
            } finally {
                if (password != null) {
                    Arrays.fill(password, '\0');
                }
                if (confirmation != null) {
                    Arrays.fill(confirmation, '\0');
                }
            }
        } catch (SQLException exception) {
            System.err.println("No fue posible crear el empleado. Revisa la conexión y la migración V1.2.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println(exception.getMessage());
        }
    }
}
