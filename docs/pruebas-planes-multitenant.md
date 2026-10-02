# Pruebas de Planes por gimnasio — issue #44

## Alcance y límites

La aplicación utiliza `Gimnasio Principal` sin asumir que su ID es 1. Planes y
las opciones de planes activos para asignar membresías se consultan por
`gimnasio_id`. Registrar, editar, activar y desactivar planes también respetan
ese contexto.

Esta guía documenta el alcance de la #44. Posteriormente se aislaron
[Miembros](avance-miembros-multitenant.md) y
[Membresías](avance-membresias-multitenant.md). Todavía no hay selector de
gimnasio ni aislamiento de accesos: no utilizar varios gimnasios con datos reales.

## Pruebas unitarias

Desde la raíz del repositorio:

```powershell
mvn clean verify
```

Comprueban la resolución y caché del gimnasio, errores por gimnasio ausente o
inactivo, recuperación de una consulta fallida, nombres vacíos, duración mínima,
normalización de nombres y bloqueo de escrituras por nombres duplicados.

No requieren PostgreSQL. Sin el perfil de integración, un `BUILD SUCCESS` sólo
confirma estas pruebas y la compilación, no el funcionamiento de los SQL reales.

## Pruebas con PostgreSQL real

### Preparación

- JDK 21, Maven y PostgreSQL 16.
- Una base **de pruebas**, separada de `sistema_gimnasio`.
- Un usuario con permiso para conectar y crear esquemas en esa base.
- Una sesión gráfica disponible para las pruebas JavaFX. Se cargan los controles
  FXML sin abrir una ventana; no se ha configurado ejecución headless en CI.

Crear la base una sola vez, desde PowerShell o pgAdmin. Si se utiliza la terminal:

```powershell
createdb -U postgres -W sistema_gimnasio_pruebas
```

Si la base ya existe, no es necesario volver a crearla. **No ejecutar los modelos
SQL ni la migración manualmente en esa base para preparar estas pruebas**: la
suite los carga dentro de sus propios esquemas temporales.

### Ejecutar

En la terminal de VS Code, desde la raíz del proyecto:

```powershell
$env:TEST_DB_URL = "jdbc:postgresql://localhost:5432/sistema_gimnasio_pruebas"
$env:TEST_DB_USER = "postgres"
$testCredential = Get-Credential -UserName $env:TEST_DB_USER -Message "Credenciales de PostgreSQL para pruebas"
$env:TEST_DB_PASSWORD = $testCredential.GetNetworkCredential().Password
mvn clean verify "-Ppostgres-it"
```

Las credenciales no se guardan en archivos. Estas variables no reemplazan las
`DB_URL`, `DB_USER` y `DB_PASSWORD` de la aplicación. El proceso de pruebas usa un
driver exclusivo de `src/test` para dirigir las llamadas de los repositorios al
esquema temporal. No es parte del JAR de la aplicación.
Si falta alguna variable `TEST_DB_*` o no se puede conectar, el perfil falla con
un error; no omite silenciosamente las pruebas ni se conecta a la base habitual.

Cada caso crea un esquema `test_planes_<identificador aleatorio>`, con tablas y
datos ficticios propios. Todas sus conexiones utilizan solamente ese esquema
en `search_path`; no utilizan ni modifican las tablas de `public`. Al terminar,
incluso si falla una aserción, la suite elimina el esquema que creó. No se borran
esquemas existentes ni se hace `DROP DATABASE`.

Si se mata el proceso o PostgreSQL deja de responder durante la limpieza, puede
quedar un esquema temporal. Revisar el nombre y la base de pruebas antes de
limpiarlo manualmente; no borrar otros esquemas.

Después de las pruebas se puede retirar la contraseña de esta terminal:

```powershell
Remove-Item Env:TEST_DB_PASSWORD
$testCredential = $null
```

### Cobertura de integración

- Resolución de `Gimnasio Principal` con un ID distinto de 1.
- Dos gimnasios con el mismo nombre de plan y distintas duraciones.
- Listas completas y de planes activos sin mezclar registros.
- Registro con gimnasio explícito, sin depender de un valor predeterminado.
- Duplicados en registro y edición, incluyendo cambios de mayúsculas y espacios
  exteriores. La edición excluye el propio ID.
- Edición que conserva propietario, estado y fecha de creación.
- Rechazo de edición y de cambios de estado con IDs ajenos o inexistentes.
- Activación y desactivación sin modificar planes del otro gimnasio.
- Gimnasio ausente/inactivo y errores SQL reales.
- Cierre explícito de `Connection`, `PreparedStatement` y `ResultSet`, también
  ante errores, filas no encontradas y resultados vacíos.
- Migración del modelo 1.0 con un plan previo: permanece visible y sin cambios;
  después se puede registrar otro plan en el mismo gimnasio.
- FXML: registro, edición, activación/desactivación, recarga, mensajes de
  validación y botones deshabilitados cuando no se puede cargar el gimnasio.
- FXML: rechazo de una selección ajena/obsoleta y advertencias cuando la escritura
  sí se completa pero falla la recarga. Para este último escenario únicamente se
  inyecta el fallo de lectura; las escrituras siguen ejecutándose en PostgreSQL.

Resultado esperado: ningún fallo, error ni prueba omitida y, al final,
`BUILD SUCCESS`. Al cerrar la #44 eran 12 pruebas unitarias y 18 de integración.
El mismo perfil ejecuta además las pruebas añadidas durante las issues #45 y
#55; consultar sus guías para el alcance de Miembros y Membresías.

Los reportes quedan en `target/surefire-reports` y `target/failsafe-reports`.
`target` está ignorado por Git.

## Comprobación visual sobre la instalación habitual

Esta revisión utiliza tu base habitual, no la base automatizada de pruebas.
Configura `DB_URL`, `DB_USER` y `DB_PASSWORD` en la misma terminal y ejecuta:

```powershell
mvn javafx:run
```

- [ ] Abrir Planes y confirmar que los planes existentes de Gimnasio Principal
  siguen visibles.
- [ ] Registrar un plan de prueba y comprobar que aparece en la tabla.
- [ ] Editarlo y verificar que nombre/duración se actualizan.
- [ ] Intentar repetir un nombre existente: debe mostrar un mensaje de error.
- [ ] Desactivar y volver a activar el plan, comprobando su estado en la tabla.
- [ ] Abrir Membresías y comprobar que un plan inactivo no aparece como opción;
  al reactivarlo y volver a abrir la vista, debe aparecer de nuevo.

Usar un plan de prueba propio para esta revisión; no cambiar planes reales sólo
para probar. La revisión visual registra datos de forma normal, no tiene rollback
automático. No crear un segundo gimnasio en la base habitual para estos pasos.

## Evidencia de esta tanda

La suite se verificó el 28 de septiembre de 2026 con Java 21 y una instancia
temporal de PostgreSQL 16, separada del servicio y de la base habitual.
Se aprobaron las 30 pruebas sin omisiones. También se comprobó que faltando la
configuración el perfil falla claramente, que no quedan esquemas temporales y
que tablas de control en `public` y en otro esquema preexistente quedan intactas.
La comprobación visual anterior queda disponible para revisar la instalación
del usuario; no se presenta como ejecutada sobre sus datos.

Herramientas de pruebas: [JUnit 5](https://docs.junit.org/5.11.4/user-guide/) y
[Maven Failsafe](https://maven.apache.org/surefire/maven-failsafe-plugin/usage.html).
