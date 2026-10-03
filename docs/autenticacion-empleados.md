# Cuentas y autenticación de empleados — issue #61

## Alcance

El empleado es una cuenta para operar la aplicación; no es un registro de la
tabla `miembro` ni representa una membresía. La migración
[`V1_2__crear_empleados.sql`](../database/migrations/V1_2__crear_empleados.sql)
crea `empleado` después del modelo multitenant 1.1:

- `empleado_id`: identificador de la cuenta.
- `gimnasio_id`: obligatorio; referencia a un solo gimnasio por empleado.
- `usuario`: único en todos los gimnasios, normalizado a minúsculas.
- `contrasena_hash`: PBKDF2-HMAC-SHA-256, 600 000 iteraciones y sal aleatoria de
  16 bytes. La contraseña original no se guarda.
- `empleado_activo` y `empleado_creado_en`: estado y fecha de creación.

La migración no crea cuentas ni una contraseña predeterminada. Tampoco se
ejecuta automáticamente. Para aplicarla en una instalación existente, primero
hacer un respaldo y verificar que la migración 1.1 esté instalada. **No hay que
aplicarla a la base habitual para ejecutar las pruebas de esta issue.**

## Flujo de autenticación preparado

`AuthenticationService.authenticate(usuario, contraseña)` consulta la cuenta
por el nombre de usuario global. El servicio compara la contraseña con el hash
y devuelve un `Employee` con su `gimnasio_id` y nombre del gimnasio solamente
si tanto la cuenta como el gimnasio están activos. No recibe un ID de gimnasio
del usuario ni utiliza el gimnasio fijo de `GymContext` para autenticar.

Usuario inexistente, contraseña incorrecta, usuario mal formado, cuenta
inactiva y gimnasio inactivo producen la misma `AuthenticationException` y el
mismo mensaje. La consulta usa `PreparedStatement`; los fallos de PostgreSQL
se propagan como `SQLException` para tratarlos como problemas de infraestructura,
no como credenciales incorrectas. La llamada recibe un `char[]`; quien llama
debe borrarlo cuando ya no lo necesite y no debe registrarlo en logs.

## Pruebas

Las pruebas unitarias del hash se ejecutan con `mvn clean verify`, sin base de
datos. Las pruebas de integración se ejecutan con `mvn clean verify
"-Ppostgres-it"` y las variables `TEST_DB_*` descritas en
[la guía de pruebas](pruebas-planes-multitenant.md). El perfil crea esquemas
temporales en una base de pruebas separada; no usa las tablas habituales.

Se verifican cuentas de dos gimnasios, usuario duplicado, contraseña errónea,
usuario inexistente, cuenta inactiva, gimnasio inactivo, cambio de asociación
del empleado, hash inválido y cierre de recursos JDBC incluso ante fallos SQL.

## Limitaciones actuales

- No existe todavía pantalla de login, cierre de sesión ni aprovisionamiento
  administrativo de empleados. La aplicación JavaFX continúa usando
  `Gimnasio Principal` mediante `GymContext`; el servicio de autenticación aún
  no controla las pantallas ni los repositorios de los módulos.
- No hay control de intentos repetidos, bloqueo temporal, auditoría de login ni
  gestión de sesiones. Esos controles deberán definirse antes de uso real.
- La aplicación de escritorio se conecta directamente a PostgreSQL con las
  credenciales `DB_*`. Si esas credenciales tienen permisos amplios, el login
  de Java no constituye una frontera segura entre gimnasios. **No utilizar
  datos reales de varios gimnasios** hasta que el contexto autenticado se
  conecte a todos los módulos y la base imponga permisos adecuados.
- No se introduce una API REST: el proyecto continúa con JavaFX y JDBC.
