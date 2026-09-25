# Modelo entidad-relación

Contiene los modelos conceptual y físico del Sistema de Gestión de Gimnasio, sus diagramas exportados y los scripts SQL para PostgreSQL.

- La versión 1.0 documenta el modelo inicial de un solo gimnasio.
- `Sistema_Gimnasio_Modelo_Fisico_v1.1_multitenant.sql` define el modelo físico para una base de datos nueva con soporte multitenant.
- Para migrar una base de datos existente, utilizar `database/migrations/V1_1__preparar_modelo_multitenant.sql` después de realizar un respaldo.
