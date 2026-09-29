# Miembros por gimnasio — primera tanda de la #45

## Implementado

- `MemberRepository.findAll()` filtra por el ID real del gimnasio actual.
- Conserva el orden por apellidos/nombres y los campos opcionales.
- Miembros reutiliza el contexto de gimnasio de la navegación.
- El selector de miembros en Membresías comparte ese mismo contexto con Planes.
- Si el gimnasio no existe, está inactivo o falla la consulta, Miembros muestra
  el error y deshabilita Guardar/Actualizar.
- Si una escritura existente se completa pero falla la recarga, se muestra una
  advertencia y no se anuncia que la lista se actualizó correctamente.

No hay un selector nuevo de gimnasio ni cambios de diseño. Si todos los miembros
actuales pertenecen a Gimnasio Principal, visualmente seguirá apareciendo la
misma lista. La diferencia es que no se muestran los de otro gimnasio.

## Pendiente para las siguientes tandas

- Enviar `gimnasio_id` explícitamente al registrar miembros.
- Limitar documentos duplicados al gimnasio actual al registrar y editar.
- Restringir la edición por `gimnasio_id` e ID de miembro.
- Probar las escrituras y la compatibilidad con miembros migrados.

Los SQL de registro, edición y comprobación de documentos **todavía no están
aislados**. El filtrado de la tabla no sustituye la protección de esas consultas.
La primera tanda sólo permite marcar la tarea “Filtrar la lista de miembros por
`gimnasio_id`”. No cerrar la #45 con este avance.

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

Las pruebas de esta tanda no intentan probar que las escrituras ya estén
aisladas: eso corresponde a los siguientes avances.

Verificado el 28 de septiembre de 2026 con PostgreSQL 16 temporal: 12 pruebas
unitarias y 30 de integración aprobadas, sin errores, fallos ni omisiones.
Incluye las regresiones de Planes y el flujo previo de registro/edición de
miembros, además de las nuevas pruebas de lectura. No se usó la base habitual.

### Revisión visual

Configurar `DB_URL`, `DB_USER` y `DB_PASSWORD` en la misma terminal y ejecutar:

```powershell
mvn javafx:run
```

1. Abrir Miembros y revisar la lista de Gimnasio Principal.
2. Seleccionar un miembro: debe habilitarse Actualizar.
3. Abrir Membresías: el selector debe ofrecer los miembros del mismo gimnasio.

No crear un segundo gimnasio en la base habitual para probar este avance;
los escenarios de dos gimnasios se ejecutan dentro de esquemas temporales.
