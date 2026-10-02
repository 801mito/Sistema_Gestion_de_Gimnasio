# Miembros por gimnasio — verificación final de la #45

## Implementado

- `MemberRepository.findAll()` filtra por el ID real del gimnasio actual.
- Conserva el orden por apellidos/nombres y los campos opcionales.
- Miembros reutiliza el contexto de gimnasio de la navegación.
- El selector de miembros en Membresías comparte ese mismo contexto con Planes.
- `MemberRepository.save()` envía el ID del gimnasio explícitamente; no depende
  del valor predeterminado de la migración.
- Al registrar, `existsByDocument()` busca sólo dentro del gimnasio actual.
  Permite repetir un documento en otro gimnasio y varios miembros sin documento.
- Al editar, `existsByDocumentExcludingId()` filtra por gimnasio y excluye al
  propio miembro: conservar su documento no es un duplicado.
- `MemberRepository.update()` exige tanto `gimnasio_id` como el ID del miembro.
  Conserva el gimnasio y la fecha de creación; no modifica registros ajenos.
- Si el ID es ajeno o ya no existe, devuelve `MemberNotFoundException` con un
  mensaje genérico de miembro no disponible en el gimnasio actual. La vista
  recarga la tabla y limpia la selección, sin anunciar un éxito ni confundirlo
  con un problema de conexión. Si falla la recarga, los botones quedan bloqueados.
- Los documentos conservan la comparación exacta del modelo existente: se
  eliminan espacios exteriores, pero no se cambia el criterio de mayúsculas.
- Si el gimnasio no existe, está inactivo o falla la consulta, Miembros muestra
  el error y deshabilita Guardar/Actualizar.
- Si una escritura existente se completa pero falla la recarga, se muestra una
  advertencia y no se anuncia que la lista se actualizó correctamente.

No hay un selector nuevo de gimnasio ni cambios de diseño. Si todos los miembros
actuales pertenecen a Gimnasio Principal, visualmente seguirá apareciendo la
misma lista. La diferencia es que no se muestran los de otro gimnasio.

## Resultado de la #45

Quedan implementados y probados el filtrado, el registro y la edición de miembros
por gimnasio, así como la comprobación de documentos duplicados al
registrar/editar. La migración 1.0 a 1.1 conserva los miembros anteriores, sus
IDs, datos y fechas de creación, y sus relaciones con membresías y códigos de
acceso. Después de migrar, esos miembros siguen visibles y editables, y se
pueden registrar nuevos. También se prueban la separación de listas y el
rechazo de IDs ajenos o inexistentes.

La verificación automatizada de la #45 está completa y la issue se cerró tras
incorporar sus cambios. No se requiere otra migración ni ejecutar scripts
adicionales en la base habitual.

El historial y la asignación de membresías quedaron fuera de la #45; su
implementación posterior se describe en
[la verificación de Membresías](avance-membresias-multitenant.md). Los códigos y
la validación de accesos siguen pendientes de aislamiento. No utilizar varios
gimnasios con datos reales todavía.

## Verificación

Se reutiliza el perfil `postgres-it` y la configuración `TEST_DB_*` de
[la guía de pruebas](pruebas-planes-multitenant.md). No requiere otra migración ni
ejecutar scripts sobre la base habitual.

Se añadieron pruebas de consultas con PostgreSQL real para dos gimnasios,
orden/mapeo de campos, lista vacía, gimnasio ausente/inactivo y errores SQL.
También se verifican el FXML de Miembros, el selector de miembros en Membresías
y el contexto compartido desde la navegación. Las pruebas JavaFX se reúnen en
`ScopedViewsIT` para utilizar una sola instancia del toolkit durante la suite.

La segunda tanda añade `MemberServiceTest` y `MemberRegistrationIT`: campos
normalizados/opcionales, registro con gimnasio explícito (incluso con un default
distinto), documentos repetidos entre gimnasios, rechazo dentro del mismo
gimnasio y protección de unicidad en PostgreSQL. También comprueba gimnasio
ausente/inactivo, errores SQL, resultados vacíos de `RETURNING` y cierre de
recursos JDBC. `ScopedViewsIT` registra mediante el botón Guardar con un contexto
secundario inyectado y comprueba el mensaje de documento duplicado.

La tercera tanda añade `MemberEditIT`: edición con gimnasio e ID, conservación
de gimnasio/fecha de creación, documento propio sin falso duplicado, documentos
de otros gimnasios, eliminación de campos opcionales y rechazo de IDs ajenos o
inexistentes. Comprueba gimnasio ausente/inactivo, errores SQL, unicidad de
PostgreSQL, `RETURNING` vacío y cierre de recursos. Amplía `MemberServiceTest` y
`ScopedViewsIT` para el botón Actualizar, documentos duplicados y selecciones
ajenas, obsoletas o eliminadas, incluso si falla la recarga.

La tanda final añade `MemberMigrationIT`: siembra miembros ficticios en el
modelo 1.0, ejecuta la migración 1.1 y comprueba conservación de IDs, nombres,
documento, campos opcionales, fecha de creación y relación con una membresía y
su código. Luego registra y edita miembros sin perder esas relaciones; verifica
duplicados dentro del gimnasio, documentos iguales entre gimnasios y rechazo de
edición ajena. `ScopedViewsIT` comprueba que la vista JavaFX muestra los miembros
anteriores y permite editarlos y registrar uno nuevo.

Los flujos JDBC de consulta, registro y edición ya se prueban ante éxito,
gimnasio no disponible, errores SQL y resultados vacíos de `RETURNING`. Cada
prueba comprueba el cierre explícito de sus recursos. La nueva prueba de mapeo
inyecta un error después de abrir el `ResultSet` de miembros y comprueba que
se cierran `ResultSet`, `PreparedStatement` y `Connection`; una consulta posterior
vuelve a funcionar.

Verificado el 30 de septiembre de 2026 con PostgreSQL 16 temporal: 21 pruebas
unitarias y 58 de integración aprobadas, sin errores, fallos ni omisiones.
Incluye las regresiones de Planes y los flujos de Miembros anteriores. No se usó
la base habitual. Al finalizar no quedaron esquemas temporales de la suite,
las tablas de control ajenas conservaron sus datos y se detuvo el servidor de
pruebas temporal.

### Revisión manual opcional

Configurar `DB_URL`, `DB_USER` y `DB_PASSWORD` en la misma terminal y ejecutar:

```powershell
mvn javafx:run
```

1. Abrir Miembros y revisar la lista de Gimnasio Principal.
2. Seleccionar un miembro: debe habilitarse Actualizar.
3. Abrir Membresías: el selector debe ofrecer los miembros del mismo gimnasio.
4. Registrar un miembro de prueba propio, con documento opcional, y verificar
   que aparece en la lista.
5. Intentar registrar otro con el mismo documento: debe indicar que ya existe en
   el gimnasio actual, sin agregar otra fila. Con documento vacío se permiten
   varios miembros, como antes.
6. Seleccionar un miembro de prueba, cambiar sus datos y actualizar manteniendo
   su documento: debe guardar, refrescar la tabla y limpiar la selección.
7. Intentar cambiar su documento por el de otro miembro del mismo gimnasio:
   debe mostrar el error sin alterar los datos guardados. También se pueden
   vaciar los campos opcionales al editar.

La vista también se probó de forma automatizada sin abrir una ventana. Los pasos
anteriores son una comprobación manual adicional y registran datos normalmente;
no tienen rollback automático. La apariencia del formulario y los ejemplos
Jaime David / Cardona Marmol no cambian.

No crear un segundo gimnasio en la base habitual para probar este avance;
los escenarios de dos gimnasios se ejecutan dentro de esquemas temporales.
