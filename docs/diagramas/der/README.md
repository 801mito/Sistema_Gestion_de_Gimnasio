# Modelo entidad-relación

Contiene los modelos conceptual y físico del Sistema de Gestión de Gimnasio, sus diagramas exportados y los scripts SQL para PostgreSQL.

- La versión 1.0 documenta el modelo inicial de un solo gimnasio.
- `Sistema_Gimnasio_Modelo_Fisico_v1.1_multitenant.sql` define el modelo físico para una base de datos nueva con soporte multitenant.
- Para migrar una base de datos existente, utilizar `database/migrations/V1_1__preparar_modelo_multitenant.sql` después de realizar un respaldo.

## Modelos editables vigentes

Los archivos CDM/PDM y las exportaciones PNG versión 1.1 se corrigieron para
alinear nombres físicos, tipos, valores iniciales, claves compuestas y
unicidades con el SQL utilizado por la aplicación. El índice de una membresía
activa por miembro es único **parcial** (`WHERE ESTADO = 'ACTIVA'`), no una
unicidad sobre todo el historial.

El PDM documenta tablas, claves y restricciones declarativas. La protección
adicional mediante funciones y disparadores se mantiene en la migración
`V1_4__proteger_aislamiento_intentos.sql`; no se deduce de las flechas del DER.
Para preparar una base, seguir el [orden de migraciones](../../../database/migrations/README.md).
No ejecutar el SQL generado desde PowerDesigner sobre la base existente.
