# Flujo de validación y registro de accesos

Esta guía describe el comportamiento actual de la aplicación desde el inicio de
sesión hasta la consulta del historial. La validación es manual: un empleado
ingresa el código en **Accesos**; no hay lector de tarjetas ni integración con
un torniquete.

## Antes de validar

1. El empleado inicia sesión con su cuenta. La cuenta determina el gimnasio de
   la sesión; no se elige un gimnasio desde la pantalla de Accesos.
2. En **Membresías**, se asigna a un miembro un plan activo. La asignación crea
   la membresía y su código de acceso en una sola operación.
3. El miembro debe tener una membresía en estado `ACTIVA`, encontrarse dentro
   de las fechas de vigencia y tener su código activo. Un código activo por sí
   solo no garantiza que se autorice la entrada.

## Decisión de la validación

En **Accesos**, el empleado escribe el código y pulsa **Validar acceso** (o
presiona Enter). El sistema recorta espacios exteriores, busca el código dentro
del gimnasio de la sesión y aplica estas reglas:

| Condición | Resultado esperado |
| --- | --- |
| Campo vacío | Rechazado: “Ingresa un código de acceso.” |
| Código inexistente en el gimnasio actual | Rechazado: “El código ingresado no existe.” |
| Código reconocido, pero inactivo | Rechazado: “El código de acceso está inactivo.” |
| Membresía `CONGELADA` o `VENCIDA` | Rechazado, mostrando el estado de la membresía |
| Fecha actual anterior al inicio | Rechazado: “La membresía aún no inicia su vigencia.” |
| Fecha actual posterior al fin | Rechazado: “La membresía está vencida.” |
| Código activo, membresía `ACTIVA` y fecha dentro de vigencia | Autorizado para el miembro asociado |

La fecha de fin se considera vigente durante ese día; se rechaza cuando la
fecha actual es posterior. Congelar una membresía no extiende ni pausa sus
fechas. Marcarla vencida tampoco requiere desactivar su código: la validación
rechaza el acceso por el estado de la membresía.

## Registro e historial

Cada validación se intenta guardar en `intento_acceso` con resultado, motivo,
fecha y hora, y el gimnasio de la sesión. Si el código corresponde a uno de ese
gimnasio, el intento conserva su asociación. Si está vacío, no existe o no
pertenece a ese gimnasio, el intento se registra sin código asociado; el texto
ingresado no se guarda.

Después de validar, el resultado aparece debajo del formulario y el historial
se actualiza. **Ver historial** muestra código (o **No reconocido**), resultado,
motivo y fecha/hora; **Actualizar historial** vuelve a consultar los registros.
El historial se filtra por el gimnasio de la sesión.

Si PostgreSQL no está disponible o no se puede guardar el intento, la pantalla
muestra un mensaje general de error. No debe considerarse autorizado un intento
cuyo resultado no se pudo registrar.

## Base de datos y límite de seguridad

En bases que comenzaron con el modelo 1.0, `intento_acceso.gimnasio_id` debe
existir antes de usar esta función. La migración `V1_3__aislar_intentos_por_gimnasio.sql`
se aplica una sola vez, después de `V1_2__crear_empleados.sql`; revisa y respalda
la base antes de aplicar migraciones manuales. Las bases nuevas creadas desde el
modelo físico multitenant ya incluyen la columna y no deben ejecutar V1.3 encima.

El filtro por gimnasio se aplica en las consultas y escrituras de la aplicación.
Como JavaFX se conecta directamente a PostgreSQL con `DB_*`, esto no reemplaza
permisos de base de datos ni constituye por sí solo una frontera de seguridad
frente a usuarios con acceso directo y amplio a PostgreSQL.

Para pasos concretos de comprobación, usa la
[guía de pruebas manuales](pruebas-manuales-acceso.md). Los resultados de esas
pruebas sólo deben marcarse como completados después de ejecutarlas en un
entorno de prueba.
