# Membresías por gimnasio — verificación de la #55

## Comportamiento implementado

- La tabla de Membresías consulta sólo las filas con el `gimnasio_id` del
  gimnasio actual. El selector de miembros y el de planes activos comparten
  ese contexto con la tabla y la navegación.
- Al asignar se verifica, en la misma transacción JDBC, que el gimnasio siga
  activo, que el miembro le pertenezca y que el plan le pertenezca y siga
  activo. Una selección antigua o un ID de otro gimnasio se rechazan antes de
  insertar, con un mensaje que no revela los datos del registro ajeno.
- La comprobación de una membresía `ACTIVA` usa `gimnasio_id` y el ID del
  miembro. La fila del miembro se bloquea durante la asignación para serializar
  dos intentos concurrentes sobre el mismo miembro; la restricción única de
  PostgreSQL sigue siendo una protección adicional.
- La inserción de la membresía incluye `gimnasio_id` explícitamente. La fecha
  final se calcula con la duración **actual guardada** del plan, incluso si la
  selección de la pantalla quedó desactualizada.
- La membresía y su código de acceso se guardan en una sola transacción. Si
  falla la creación del código o una validación, se hace rollback; no queda
  una membresía nueva sin código.
- Las membresías anteriores a la migración 1.1 continúan visibles con su
  miembro y plan. Se puede asignar una nueva membresía a un miembro anterior
  que no tenga otra activa.

No se agregó un selector de gimnasio ni cambió la apariencia de la pantalla:
la instancia sigue utilizando **Gimnasio Principal**. Esta issue aísla las
lecturas y la asignación de Membresías, pero no cambia los estados de las
membresías (`CONGELADA`/`VENCIDA`).

## Pruebas y evidencia

Sin PostgreSQL, desde la raíz del repositorio:

```powershell
mvn clean verify
```

Para las pruebas JDBC y JavaFX, utilizar una base **de pruebas** y las variables
`TEST_DB_URL`, `TEST_DB_USER` y `TEST_DB_PASSWORD` siguiendo
[la preparación del perfil](pruebas-planes-multitenant.md#pruebas-con-postgresql-real).
Luego ejecutar:

```powershell
mvn clean verify "-Ppostgres-it"
```

El perfil no usa las variables `DB_*` de la aplicación ni las tablas de la base
habitual. Cada caso crea y elimina su propio esquema temporal con datos
ficticios; no requiere ejecutar de nuevo la migración en la base habitual.

`MembershipRepositoryReadIT` verifica la separación de historiales entre dos
gimnasios, orden, datos anteriores a la migración, lista vacía, gimnasio
ausente/inactivo y cierre JDBC ante errores. `MembershipAssignmentIT` cubre
asignaciones de ambos gimnasios, rechazo de miembro/plan ajeno o desactualizado,
gimnasio desactivado, duplicados activos, duración vigente del plan y rollback
si falla la inserción del código. `ScopedViewsIT` comprueba la tabla, los
selectores y el botón Asignar sin abrir una ventana. Las pruebas revisan el
cierre explícito de los recursos JDBC.

En la última ejecución sobre PostgreSQL 16 temporal pasaron 21 pruebas
unitarias y 72 de integración, sin fallos ni omisiones. No se usó la base
habitual; al terminar, los esquemas de prueba se habían retirado y el servidor
temporal quedó detenido.

### Comprobación manual opcional

Con `DB_URL`, `DB_USER` y `DB_PASSWORD` configuradas en la misma terminal,
ejecutar `mvn javafx:run`. En Membresías, el historial existente de Gimnasio
Principal debe seguir visible. Para probar una asignación sin alterar datos
reales, usar **una base y registros de prueba**: escoger allí un miembro sin
membresía activa y un plan activo, asignar y comprobar que aparece en la tabla
y se muestra el código. Un segundo intento para el mismo miembro debe indicar
que ya tiene una membresía activa. Estas acciones persisten datos y no tienen
rollback automático.

## Límites pendientes

La lista, activación/desactivación y validación de códigos de acceso todavía
no están aisladas por gimnasio. Los intentos de acceso aún no se registran en
la aplicación. Tampoco hay autenticación ni selección de gimnasio. Por tanto,
**no utilizar un segundo gimnasio con datos reales todavía**. El aislamiento
de accesos debe abordarse antes de considerar la aplicación multitenant para
uso real.
