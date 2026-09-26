# Migraciones de la base de datos

Esta carpeta contiene cambios que deben aplicarse sobre una base de datos existente y no se ejecutan automáticamente al iniciar la aplicación.

## Versión 1.1 multitenant

`V1_1__preparar_modelo_multitenant.sql` crea el gimnasio inicial y asocia los planes, miembros y membresías existentes con ese gimnasio.

La migración configura temporalmente `Gimnasio Principal` como valor predeterminado. Esto permite que la versión actual de la aplicación continúe registrando planes, miembros y membresías mientras se implementa el contexto de gimnasio. No se debe crear un segundo gimnasio para uso real hasta que los repositorios filtren por `gimnasio_id`.

Antes de ejecutarla:

1. Realizar un respaldo de la base de datos `sistema_gimnasio`.
2. Confirmar que la aplicación ya está preparada para trabajar con `gimnasio_id`.
3. Ejecutar la migración completa una sola vez desde **Query Tool** de pgAdmin 4.
4. Ejecutar `database/tests/validar_modelo_multitenant.sql`.

No ejecutar el modelo físico para bases nuevas sobre una base de datos que ya contiene información.
