# Códigos y validación de acceso por gimnasio — verificación de la #59

## Comportamiento implementado

- La tabla Accesos consulta sólo códigos vinculados a membresías del gimnasio
  actual. Conserva el orden de fecha de creación y muestra el miembro y plan
  asociados. La navegación comparte el mismo `GymContext` con las demás vistas.
- Activar o desactivar un código comprueba su ID y la pertenencia de su
  membresía al gimnasio actual **en el mismo `UPDATE`**. También exige que el
  gimnasio siga activo, incluso si el contexto se había resuelto antes de su
  desactivación. Los IDs ajenos, inexistentes u obsoletos producen el mismo
  mensaje genérico; la pantalla limpia la selección y recarga la tabla.
- La validación consulta el código sólo dentro del gimnasio actual. Un código
  de otro gimnasio se rechaza como inexistente, sin mostrar el nombre del
  miembro, el plan ni el estado de la membresía ajena. Se conservan las reglas
  existentes para código inactivo, membresía no activa y fechas de vigencia.
- La generación y la restricción única de PostgreSQL siguen comprobando la
  unicidad del valor **en toda la base**, no por gimnasio. La membresía y su
  código siguen guardándose en una sola transacción.
- Los códigos anteriores a la migración multitenant continúan visibles en
  Gimnasio Principal y pueden cambiar de estado. No se requiere otra migración
  SQL: el gimnasio se obtiene mediante la membresía ya relacionada.

La aplicación todavía usa **Gimnasio Principal** como contexto inicial; no se
añadió una pantalla de inicio de sesión ni un selector de gimnasio.

## Pruebas

Desde la raíz del repositorio, sin PostgreSQL:

```powershell
mvn clean verify
```

Para ejecutar también las pruebas JDBC y JavaFX, preparar una base **de
pruebas** y las variables `TEST_DB_URL`, `TEST_DB_USER` y `TEST_DB_PASSWORD` como
indica [la guía del perfil](pruebas-planes-multitenant.md#pruebas-con-postgresql-real).
Después:

```powershell
mvn clean verify "-Ppostgres-it"
```

`AccessCodeRepositoryIT` cubre dos gimnasios ficticios, lista y orden, cambios
de estado propios/ajenos, gimnasio inactivo, código inexistente, reglas de
vigencia, unicidad global, registros migrados y cierre de los recursos JDBC
incluso ante errores. `ScopedViewsIT` verifica la vista FXML, la navegación,
los botones y la validación sin abrir una ventana. Cada prueba JDBC utiliza su
propio esquema temporal y no modifica las tablas de la base habitual.

En la verificación final sobre PostgreSQL 16 temporal pasaron 21 pruebas
unitarias y 83 de integración, sin fallos ni omisiones. No se utilizó la base
habitual.

### Comprobación manual opcional

En una **base de prueba** con la migración 1.1 aplicada, configurar `DB_URL`,
`DB_USER` y `DB_PASSWORD` en la misma terminal y ejecutar `mvn javafx:run`.
En Accesos deben aparecer los códigos de Gimnasio Principal. Validar uno
vigente y otro inexistente; activar/desactivar un código propio y comprobar
que la tabla refleja el cambio. Estas operaciones persisten datos: no hacerlas
en la base habitual sólo para comprobar esta issue. La comprobación de dos
gimnasios se realiza mediante las pruebas de integración; la interfaz todavía
no permite cambiar de gimnasio.

## Límites pendientes

Los intentos de acceso aún no se registran ni se aíslan por gimnasio. Tampoco
hay cuentas de usuario, autenticación ni elección **autorizada** del gimnasio.
El filtrado de esta issue evita cruces accidentales en sus operaciones, pero
**no equivale a un multitenant seguro para uso real**. No utilizar un segundo
gimnasio con datos reales hasta completar esas etapas.
