# Sistema de Gestión de Gimnasio

Sistema para gestionar miembros, membresías y validar accesos según el estado
de la membresía.

## Tecnologías

- Java 21 LTS.
- JavaFX 21.
- Maven.
- PostgreSQL 16.
- JDBC y el controlador PostgreSQL para la conexión a la base de datos.

## Alcance del MVP

- Registrar miembros.
- Asignar planes y membresías.
- Generar códigos de acceso.
- Manejar membresías activas, congeladas y vencidas.
- Validar accesos.
- Registrar intentos de acceso.

## Ejecutar la aplicación

### Requisitos

- JDK 21 o superior instalado.
- Maven instalado y disponible en la terminal.

### Comando

Desde la raíz del repositorio, ejecutar:

```powershell
mvn clean javafx:run
```

La primera ejecución descarga las dependencias de JavaFX y PostgreSQL JDBC.

## Conexión a PostgreSQL

La aplicación no almacena credenciales en el repositorio. Antes de ejecutar el
verificador de conexión, define estas variables **en la misma terminal de
PowerShell**:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/sistema_gimnasio"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "tu_contraseña"
```

Para validar que existe conexión y que PostgreSQL responde, ejecuta:

```powershell
mvn exec:java
```

El verificador abre la conexión, ejecuta `SELECT 1` y la cierra
automáticamente. Si no se configuró alguna variable o la conexión falla,
muestra un mensaje claro sin imprimir la contraseña.
