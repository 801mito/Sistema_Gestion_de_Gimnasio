# Migraciones de la base de datos

Esta carpeta contiene cambios que deben aplicarse sobre una base de datos existente y no se ejecutan automáticamente al iniciar la aplicación.

## Versión 1.2: cuentas de empleados (issue #61)

`V1_2__crear_empleados.sql` crea la tabla `empleado` después del modelo 1.1.
Cada cuenta tiene un único `gimnasio_id` obligatorio y un usuario único en toda
la aplicación. La migración no crea cuentas ni contraseñas predeterminadas.
Antes de aplicarla a la base habitual, realizar un respaldo y revisar la tanda;
la aplicación JavaFX todavía no utiliza estas cuentas para iniciar sesión.
Las contraseñas se almacenan como hashes PBKDF2-HMAC-SHA-256 con sal aleatoria,
no como texto plano.

Las pruebas automáticas aplican esta migración sólo dentro de esquemas temporales
de una base de pruebas. No es necesario ejecutarla en la base habitual para
verificar esta issue.
Ver [el flujo y las limitaciones de autenticación](../../docs/autenticacion-empleados.md).

## Versión 1.1 multitenant

`V1_1__preparar_modelo_multitenant.sql` crea el gimnasio inicial y asocia los planes, miembros y membresías existentes con ese gimnasio.

La migración configura temporalmente `Gimnasio Principal` como valor predeterminado. Esto permite que la versión actual de la aplicación continúe registrando planes, miembros y membresías mientras se implementa el contexto de gimnasio. No se debe crear un segundo gimnasio para uso real hasta que los repositorios filtren por `gimnasio_id`.

Antes de ejecutarla:

1. Realizar un respaldo de la base de datos `sistema_gimnasio`.
2. Confirmar que la aplicación ya está preparada para trabajar con `gimnasio_id`.
3. Ejecutar la migración completa una sola vez desde **Query Tool** de pgAdmin 4.
4. Ejecutar `database/tests/validar_modelo_multitenant.sql`.

No ejecutar el modelo físico para bases nuevas sobre una base de datos que ya contiene información.

## Estado del uso desde Java

Planes ya utiliza el contexto del gimnasio actual, filtra sus consultas y envía
`gimnasio_id` explícitamente al registrar. También restringe edición y cambios de
estado al mismo gimnasio. Los valores predeterminados de la migración se
conservan por compatibilidad con los módulos que todavía no se han adaptado.

Las pruebas de aislamiento de Planes no requieren aplicar otra migración a la
base habitual: reconstruyen los modelos 1.0/1.1 en esquemas temporales de una
base de pruebas. Ver [la guía de pruebas](../../docs/pruebas-planes-multitenant.md).
