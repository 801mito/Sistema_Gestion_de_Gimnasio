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

Esto **no significa que la aplicación ya sea multitenant para uso real**: los
intentos de acceso todavía no se registran ni aíslan por gimnasio. Tampoco hay
inicio de sesión ni selección autorizada de gimnasio. No utilizar un segundo
gimnasio con datos reales hasta completar esas partes.

La #45 verifica que la tabla de Miembros y el selector de miembros en Membresías
consultan por gimnasio. El registro envía `gimnasio_id` explícitamente y comprueba
documentos duplicados sólo dentro del gimnasio actual. La edición también filtra
por gimnasio e ID, y excluye al propio miembro al comprobar documentos duplicados.
Las pruebas comprueban además que los miembros y relaciones anteriores a la
migración permanecen y que los recursos JDBC se cierran correctamente. Ver
[la verificación de Miembros](docs/avance-miembros-multitenant.md).

La #55 filtra el historial de Membresías por gimnasio. Al asignar, verifica que
el gimnasio esté activo y que el miembro y el plan le pertenezcan, comprueba la
membresía activa dentro de ese gimnasio y guarda `gimnasio_id` explícitamente.
La asignación y la generación del código permanecen en una sola transacción:
si falla el código, se revierte también la membresía. Ver
[la verificación de Membresías](docs/avance-membresias-multitenant.md).

La #59 filtra los códigos y la validación de acceso por el gimnasio actual a
través de la membresía asociada. Un código de otro gimnasio no aparece, no
puede activarse o desactivarse por ID y se rechaza igual que uno inexistente.
La generación conserva la unicidad global de los códigos. Ver
[la verificación de Accesos](docs/avance-accesos-multitenant.md).

La #61 añade cuentas de empleados vinculadas a un gimnasio y un servicio que
verifica sus credenciales. Aún no hay pantalla de login ni se usa esa identidad
para reemplazar el `GymContext` fijo. Ver
[el alcance y las limitaciones de la autenticación](docs/autenticacion-empleados.md).

## Pruebas

Las pruebas unitarias no requieren PostgreSQL ni credenciales:

```powershell
mvn clean verify
```

Las pruebas de integración se ejecutan únicamente con el perfil `postgres-it`
y utilizan variables `TEST_DB_*` separadas de las de la aplicación. Crean y
eliminan esquemas temporales en una base de pruebas.

Consultar [la guía de pruebas con PostgreSQL](docs/pruebas-planes-multitenant.md)
para configurar el perfil; las guías de cada módulo explican su cobertura.
`mvn clean verify` sin el perfil **no** verifica la integración con PostgreSQL.
