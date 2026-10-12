# Congelar y reactivar membresías (#35)

En la vista **Membresías**, selecciona una fila para habilitar la acción que
corresponde a su estado:

- **Congelar membresía** cambia `ACTIVA` a `CONGELADA`.
- **Reactivar membresía** cambia `CONGELADA` a `ACTIVA` solamente si su fecha
  final no ha pasado y el miembro no tiene otra membresía activa.

La membresía conserva sus fechas de inicio y fin y permanece en el historial.
Congelar **no pausa ni extiende** su vigencia. Tampoco cambia el estado del
código de acceso: aunque el código siga activo, la validación rechaza la
entrada cuando la membresía está congelada. Si el código se desactivó por
separado, reactivar la membresía no lo activa automáticamente.

Las operaciones sólo pueden modificar membresías del gimnasio de la sesión
actual. La selección y los mensajes se actualizan después de cada cambio.
Esta función no gestiona cobros, pagos ni renovaciones. Marcar una membresía
como `VENCIDA` pertenece a la issue #37.

## Verificación

Las pruebas de integración usan una base PostgreSQL de pruebas, no la base
habitual. Tras configurar `TEST_DB_URL`, `TEST_DB_USER` y `TEST_DB_PASSWORD`,
ejecuta:

```powershell
mvn clean verify -Ppostgres-it
```

Las pruebas comprueban las transiciones, la restricción por gimnasio, el
rechazo de acceso durante el congelamiento, la conservación de fechas y del
estado del código, los rechazos de reactivación y el cierre de recursos JDBC.
