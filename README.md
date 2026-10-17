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
- PostgreSQL 16 con el esquema actualizado, la migración de empleados y, si la
  base proviene del modelo anterior, la migración 1.3 de intentos por gimnasio;
  también se necesita una cuenta de empleado activa.

### Comando

Desde la raíz del repositorio, ejecutar:

```powershell
mvn clean javafx:run
```

La primera ejecución descarga las dependencias de JavaFX y PostgreSQL JDBC.
Configura las variables de conexión indicadas abajo **antes** de iniciar la
aplicación y en la misma terminal.

La aplicación ahora abre en el inicio de sesión y no muestra los módulos sin
una cuenta válida. Para preparar la primera cuenta localmente, sigue
[la guía de autenticación](docs/autenticacion-empleados.md#preparar-el-primer-empleado-avance-de-la-issue-64).

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

## Flujo de acceso y aislamiento por gimnasio

El inicio de sesión vincula la sesión del empleado con un solo gimnasio. Miembros,
Planes, Membresías y Accesos usan ese contexto para consultar y modificar datos.
La validación revisa el código, el estado de la membresía y sus fechas; cada
intento queda en el historial del gimnasio, incluso si el código no existe.
Consulta el [flujo de acceso](docs/flujo-acceso.md) y la
[guía de pruebas manuales](docs/pruebas-manuales-acceso.md).

Las guías de [Miembros](docs/avance-miembros-multitenant.md),
[Membresías](docs/avance-membresias-multitenant.md),
[códigos e intentos de acceso](docs/avance-accesos-multitenant.md),
[estados de membresías](docs/estados-membresias.md) y
[autenticación](docs/autenticacion-empleados.md) detallan el comportamiento de
cada módulo.

El aislamiento de consultas dentro de la aplicación **no sustituye** permisos
de PostgreSQL: el cliente JavaFX se conecta directamente a la base usando
`DB_*`. No expongas una base con datos reales de varios gimnasios a usuarios que
compartan credenciales con permisos amplios; revisa las limitaciones de
[autenticación](docs/autenticacion-empleados.md) antes de un despliegue real.

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
