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
- PostgreSQL 16 y la migración multitenant 1.1 aplicada para utilizar los módulos con datos.

### Comando

Desde la raíz del repositorio, ejecutar:

```powershell
mvn clean javafx:run
```

La primera ejecución descarga las dependencias de JavaFX y PostgreSQL JDBC.
Configura las variables de conexión indicadas abajo **antes** de iniciar la
aplicación y en la misma terminal.

## Conexión a PostgreSQL

La aplicación no almacena credenciales en el repositorio. Antes de ejecutar el
verificador de conexión, define estas variables **en la misma terminal de
PowerShell**:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/sistema_gimnasio"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "CONTRASEÑA"
```

Para validar que existe conexión y que PostgreSQL responde, ejecuta:

```powershell
mvn exec:java
```

El verificador abre la conexión, ejecuta `SELECT 1` y la cierra
automáticamente. Si no se configuró alguna variable o la conexión falla,
muestra un mensaje claro sin imprimir la contraseña.

## Estado del aislamiento por gimnasio

El módulo de Planes consulta, registra, edita, activa y desactiva únicamente los
planes del gimnasio actual. Por ahora se utiliza `Gimnasio Principal`, obtenido
de PostgreSQL por su nombre; no se supone que su ID sea 1.

Los nombres duplicados se validan dentro de ese gimnasio. Dos gimnasios pueden
tener un plan con el mismo nombre sin compartir sus registros.

Esto **no significa que toda la aplicación ya esté aislada**: todavía están
pendientes miembros, membresías y accesos. No hay selector de gimnasio ni inicio
de sesión. No utilizar un segundo gimnasio con datos reales hasta completar esas
partes.

Avance de la #45: la tabla de Miembros y el selector de miembros en Membresías
consultan por gimnasio. El registro envía `gimnasio_id` explícitamente y comprueba
documentos duplicados sólo dentro del gimnasio actual. La edición y su validación
de documentos siguen pendientes de aislamiento. Ver [el avance de Miembros](docs/avance-miembros-multitenant.md).

## Pruebas de Planes

Las pruebas unitarias no requieren PostgreSQL ni credenciales:

```powershell
mvn clean verify
```

Las pruebas de integración se ejecutan únicamente con el perfil `postgres-it`
y utilizan variables `TEST_DB_*` separadas de las de la aplicación. Crean y
eliminan esquemas temporales en una base de pruebas.

Consultar [la guía de pruebas de Planes](docs/pruebas-planes-multitenant.md)
para los comandos, cobertura y comprobación visual. `mvn clean verify` sin el
perfil **no** verifica la integración con PostgreSQL.
