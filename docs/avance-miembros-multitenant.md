# Miembros por gimnasio — avance parcial de la #45

## Implementado

- `MemberRepository.findAll()` filtra por el ID real del gimnasio actual.
- Conserva el orden por apellidos/nombres y los campos opcionales.
- Miembros reutiliza el contexto de gimnasio de la navegación.
- El selector de miembros en Membresías comparte ese mismo contexto con Planes.
- `MemberRepository.save()` envía el ID del gimnasio explícitamente; no depende
  del valor predeterminado de la migración.
- Al registrar, `existsByDocument()` busca sólo dentro del gimnasio actual.
  Permite repetir un documento en otro gimnasio y varios miembros sin documento.
- Los documentos conservan la comparación exacta del modelo existente: se
  eliminan espacios exteriores, pero no se cambia el criterio de mayúsculas.
- Si el gimnasio no existe, está inactivo o falla la consulta, Miembros muestra
  el error y deshabilita Guardar/Actualizar.
- Si una escritura existente se completa pero falla la recarga, se muestra una
  advertencia y no se anuncia que la lista se actualizó correctamente.

No hay un selector nuevo de gimnasio ni cambios de diseño. Si todos los miembros
actuales pertenecen a Gimnasio Principal, visualmente seguirá apareciendo la
misma lista. La diferencia es que no se muestran los de otro gimnasio.

## Pendiente para las siguientes tandas

- Limitar documentos duplicados al gimnasio actual al editar.
- Restringir la edición por `gimnasio_id` e ID de miembro.
- Completar las pruebas de edición y de compatibilidad con miembros existentes
  antes de la migración.

Los SQL de edición y comprobación de documentos al editar **todavía no están
aislados**. El filtrado de la tabla no sustituye la protección de esas consultas.
Con estas dos tandas se pueden marcar “Filtrar la lista de miembros por
`gimnasio_id`” e “Incluir `gimnasio_id` al registrar un miembro”. Las tareas que
incluyen edición deben seguir pendientes. No cerrar la #45 con este avance.

El historial y la asignación de membresías, y los códigos/accesos, quedan fuera
de esta tanda. No utilizar varios gimnasios con datos reales todavía.

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

No se presenta la edición como aislada ni probada para varios gimnasios.

Verificado el 28 de septiembre de 2026 con PostgreSQL 16 temporal: 17 pruebas
unitarias y 40 de integración aprobadas, sin errores, fallos ni omisiones.
Incluye las regresiones de Planes y el flujo previo de registro/edición de
miembros, además de las pruebas de lectura y registro por gimnasio. No se usó
la base habitual. Al finalizar no quedaron esquemas temporales de la suite y
las tablas de control ajenas a esos esquemas conservaron sus datos.

### Revisión visual

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

Estos pasos registran datos normalmente; no tienen rollback automático. La
apariencia del formulario y los ejemplos Jaime David / Cardona Marmol no cambian.

No crear un segundo gimnasio en la base habitual para probar este avance;
los escenarios de dos gimnasios se ejecutan dentro de esquemas temporales.
