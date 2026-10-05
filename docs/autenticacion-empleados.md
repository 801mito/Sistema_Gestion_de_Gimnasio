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

## Preparar el primer empleado (avance de la issue #64)

Tras aplicar `V1_2__crear_empleados.sql` en **una base de prueba o instalación
elegida conscientemente**, se puede crear la primera cuenta de cada gimnasio
activo desde una terminal local. Esta herramienta no crea un gimnasio, no usa
contraseña predeterminada, no recibe la clave por argumentos y no permite una
segunda cuenta inicial para el mismo gimnasio. No ejecutar la migración ni esta
herramienta sobre la base habitual sólo para correr las pruebas automatizadas.

Con `DB_URL`, `DB_USER` y `DB_PASSWORD` configurados para la base elegida:

```powershell
mvn -q "-DskipTests" package
mvn -q "-DincludeScope=runtime" dependency:build-classpath "-Dmdep.outputFile=target/runtime-classpath.txt"
$runtimeClasspath = (Get-Content -LiteralPath target/runtime-classpath.txt -Raw).Trim()
java -cp "target/classes;$runtimeClasspath" gt.edu.gimnasio.app.EmployeeBootstrap
```

La herramienta muestra el destino de la base sin parámetros de conexión y pide
el nombre exacto de un gimnasio existente, usuario, contraseña y confirmación
interactiva (`CREAR`). La clave
no se imprime ni queda en la línea de comandos. Debe usarse en una terminal
interactiva real; sin ella, se cancela. El acceso a esta herramienta y a las
credenciales de PostgreSQL debe limitarse a quien administra la instalación.

`GymContext` se construye con el `Employee` devuelto por la autenticación y
resuelve su gimnasio por ID. Al iniciar, JavaFX muestra el login; sólo después
de autenticar carga la ventana principal con ese contexto compartido. Miembros,
Planes, Membresías y Accesos lo reciben desde la navegación. El empleado y
gimnasio se muestran como información de solo lectura, sin selector. Al pulsar
**Cerrar sesión**, se descartan el contexto y las vistas de la sesión anterior,
se desactiva su navegación y vuelve a mostrarse el login. Hay que autenticarse
de nuevo para consultar o modificar datos desde la interfaz.

## Pruebas

Las pruebas unitarias del hash se ejecutan con `mvn clean verify`, sin base de
datos. Las pruebas de integración se ejecutan con `mvn clean verify
"-Ppostgres-it"` y las variables `TEST_DB_*` descritas en
[la guía de pruebas](pruebas-planes-multitenant.md). El perfil crea esquemas
temporales en una base de pruebas separada; no usa las tablas habituales.

Se verifican cuentas de dos gimnasios, usuario duplicado, contraseña errónea,
usuario inexistente, cuenta inactiva, gimnasio inactivo, cambio de asociación
del empleado, hash inválido y cierre de recursos JDBC incluso ante fallos SQL.
Una prueba JavaFX adicional cubre credenciales inválidas, login, aislamiento
de Miembros, Planes, Membresías y Accesos, cierre de sesión, bloqueo de la
navegación anterior y nuevo login con otro gimnasio. También intenta modificar
un plan y un código ajenos y verifica que permanezcan intactos.

## Limitaciones actuales

- El aprovisionamiento inicial es una herramienta local, no un panel
  administrativo. Los constructores sin argumentos que usan el gimnasio fijo
  permanecen para pruebas y código legado, pero el arranque normal pasa el
  contexto autenticado a la navegación.
- No hay control de intentos repetidos, bloqueo temporal, auditoría de login ni
  gestión de sesiones. Esos controles deberán definirse antes de uso real.
- La aplicación de escritorio se conecta directamente a PostgreSQL con las
  credenciales `DB_*`. Si esas credenciales tienen permisos amplios, el login
  de Java no constituye una frontera segura entre gimnasios. **No utilizar
  datos reales de varios gimnasios** hasta que el contexto autenticado se
  conecte a todos los módulos y la base imponga permisos adecuados.
- No se introduce una API REST: el proyecto continúa con JavaFX y JDBC.
