# Migraciones de la base de datos

Esta carpeta contiene cambios que deben aplicarse sobre una base de datos existente y no se ejecutan automáticamente al iniciar la aplicación.

## Versión 1.4: integridad de intentos de acceso (issue #81)

Aplicar `V1_4__proteger_aislamiento_intentos.sql` una sola vez en ambas rutas:

- **Base nueva:** modelo físico multitenant 1.1 → V1_2 → V1_4. El modelo físico ya incluye `intento_acceso.gimnasio_id`.
- **Base del modelo 1.0:** V1_1 → V1_2 → V1_3 → V1_4, aplicando sólo las migraciones pendientes.

Respaldar la base antes de migrar y ejecutar V1_4 en el esquema que contiene las tablas del sistema.
La migración rechaza nuevas asociaciones entre intentos y códigos de distintos gimnasios y evita que un traslado de una membresía o código introduzca ese cruce. No modifica los intentos históricos: si uno ya tiene una asociación incorrecta, la aplicación conserva su resultado, motivo y fecha y lo muestra como «No reconocido».

## Versión 1.3: intentos por gimnasio (issue #36)

`V1_3__aislar_intentos_por_gimnasio.sql` agrega `gimnasio_id` obligatorio a
`intento_acceso`. Los intentos que ya tienen código reciben el gimnasio de su
membresía. Los intentos antiguos sin código se asignan a `Gimnasio Principal`,
el único gimnasio que existía cuando se registraron. La aplicación registra
cada validación y filtra el historial por el gimnasio autenticado. Revisar un
respaldo antes de ejecutar esta migración una sola vez después de V1_2.
El modelo físico 1.1 para bases nuevas ya incluye esta columna; no se debe
ejecutar V1_3 sobre una base creada directamente con ese modelo.

## Versión 1.2: cuentas de empleados (issue #61)

`V1_2__crear_empleados.sql` crea la tabla `empleado` después del modelo 1.1.
Cada cuenta tiene un único `gimnasio_id` obligatorio y un usuario único en toda
la aplicación. La migración no crea cuentas ni contraseñas predeterminadas.
Antes de aplicarla a la base habitual, realizar un respaldo y revisar la tanda;
la aplicación JavaFX utiliza estas cuentas para iniciar sesión.
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

Planes, Miembros, Membresías y Accesos utilizan el contexto del gimnasio actual
para filtrar sus consultas y restringir las escrituras. Los valores
predeterminados de las migraciones se conservan por compatibilidad con el
modelo previo y las pruebas.

Las pruebas de aislamiento de Planes no requieren aplicar otra migración a la
base habitual: reconstruyen los modelos 1.0/1.1 en esquemas temporales de una
base de pruebas. Ver [la guía de pruebas](../../docs/pruebas-planes-multitenant.md).
El flujo de validación y registro de intentos está descrito en
[la guía de Accesos](../../docs/flujo-acceso.md).
