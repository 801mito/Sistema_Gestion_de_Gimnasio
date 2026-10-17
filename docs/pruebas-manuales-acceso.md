# Guía de pruebas manuales del flujo de acceso

Esta lista sirve para comprobar el flujo de la pantalla **Accesos** en una
instalación controlada. Los resultados quedan en la base de datos: usa una base
de pruebas desechable, no la base habitual ni una base con datos reales.

## Preparación segura

1. Configura `DB_URL`, `DB_USER` y `DB_PASSWORD` en la terminal de PowerShell
   para que apunten exclusivamente a la base de pruebas. Revisa el nombre de la
   base en `DB_URL` antes de continuar; no copies aquí credenciales reales.
2. Confirma que la base tiene el esquema vigente y la tabla `empleado`. Si fue
   creada desde el modelo 1.0, debe tener aplicadas, en orden,
   `V1_1__preparar_modelo_multitenant.sql`,
   `V1_2__crear_empleados.sql` y
   `V1_3__aislar_intentos_por_gimnasio.sql`. Una base nueva creada desde el
   modelo físico multitenant ya tiene la columna `intento_acceso.gimnasio_id`;
   no ejecutes V1.3 otra vez. No vuelvas a ejecutar migraciones ya aplicadas.
3. Inicia la aplicación con `mvn clean javafx:run` y entra con un empleado de
   prueba asociado a un gimnasio de prueba.
4. Prepara un miembro de prueba, un plan activo y una membresía vigente con un
   código activo. Anota el código sólo para usarlo durante la comprobación.
   Prepara códigos/membresías independientes para los casos de código
   desactivado, membresía congelada y vencida, ya que esos cambios persisten.

## Casos

Completa la columna **Resultado** después de ejecutar cada caso. No marques
ninguno como aprobado sólo por leer esta guía.

| Caso | Acción | Resultado esperado | Resultado |
| --- | --- | --- | --- |
| Código vigente | Validar el código de la membresía activa | Se autoriza al miembro y se agrega un intento `AUTORIZADO` al historial | Aprobado el 17/10/2026: autorización e intento visibles |
| Código inactivo | En **Ver códigos**, desactivar el código de prueba y validarlo | Se rechaza por código inactivo; queda un intento `RECHAZADO` asociado al código | Aprobado el 17/10/2026: rechazo e intento asociado al código visibles |
| Membresía congelada | Congelar una membresía de prueba cuyo código siga activo y validarlo | Se rechaza indicando que la membresía está congelada; el intento queda asociado al código | Aprobado el 17/10/2026: rechazo e intento visibles |
| Membresía vencida | Marcar como vencida una membresía de prueba con código activo y validarlo | Se rechaza indicando que la membresía está vencida; el intento queda asociado al código | Aprobado el 17/10/2026: rechazo e intento visibles |
| Código desconocido | Ingresar un valor que no corresponda a ningún código y validarlo | Se rechaza como inexistente; el historial muestra **No reconocido** y no conserva el valor ingresado | Aprobado el 17/10/2026: rechazo e intento no reconocido visibles |
| Campo vacío | Dejar el campo vacío y validar | Se rechaza indicando que se ingrese un código; el historial muestra **No reconocido** | Aprobado el 17/10/2026: rechazo e intento no reconocido visibles |
| Historial | Abrir **Ver historial** y luego **Actualizar historial** | Aparecen los intentos de la sesión con código, resultado, motivo y fecha/hora, en orden del más reciente al más antiguo | Aprobado el 17/10/2026: intentos, resultados y motivos visibles, incluido el rechazo por código inactivo |

Usa un valor ficticio que no sea un código real para el caso de código
desconocido. Si reutilizas un código entre casos, reactívalo antes de probar la
membresía congelada; no intentes reactivar una membresía vencida.

Si se quiere comprobar el aislamiento visual entre gimnasios, prepara en esa
misma base de prueba un segundo gimnasio, cuenta y datos ficticios; inicia sesión
con cada cuenta y comprueba que sólo ve sus códigos e historial. No crees un
segundo gimnasio en una base habitual para probarlo. La separación entre
gimnasios también está cubierta por las pruebas automatizadas de integración.

## Cierre de la comprobación

- Revisa que cada validación realizada aparezca en el historial con su resultado
  y motivo correctos.
- No esperes que estas acciones se reviertan: congelar, vencer y desactivar un
  código son cambios persistentes en esa base.
- Anota cualquier diferencia entre el resultado observado y el esperado antes
  de reportar la tanda como terminada.
- Cuando termines, cierra la aplicación. Puedes descartar la base de pruebas
  completa según el procedimiento local de respaldo/eliminación; no borres
  manualmente datos de una base compartida o habitual.
