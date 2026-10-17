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
  SQL para el alcance de la #59: el gimnasio se obtiene mediante la membresía
  ya relacionada. El registro posterior de intentos requiere la #36 y su
  migración 1.3 en bases que comenzaron con el modelo anterior.

El contexto de Accesos proviene del empleado autenticado; no hay un selector
manual de gimnasio. El flujo de validación y el historial están descritos en
[la guía actual de acceso](flujo-acceso.md).

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

En una **base de prueba** con el esquema actualizado y una cuenta de empleado,
configurar `DB_URL`, `DB_USER` y `DB_PASSWORD` en la misma terminal y ejecutar
`mvn clean javafx:run`. Iniciar sesión y abrir Accesos. Validar un código de
prueba vigente y uno desconocido; activar/desactivar un código propio y
comprobar que la tabla refleja el cambio. Estas operaciones persisten datos:
no hacerlas en la base habitual sólo para comprobar esta issue. Para el registro
e historial de intentos, seguir la
[guía actual de pruebas manuales](pruebas-manuales-acceso.md).

## Límites pendientes

El registro y aislamiento de intentos se añadieron posteriormente en la #36;
las cuentas de empleados y el contexto autenticado se completaron en las #61 y
#64. El cliente JavaFX aún se conecta directamente a PostgreSQL con `DB_*`, por
lo que el filtro de la aplicación no reemplaza permisos de base de datos. Revisa
[las limitaciones de autenticación](autenticacion-empleados.md) antes de usar
datos reales de varios gimnasios.
