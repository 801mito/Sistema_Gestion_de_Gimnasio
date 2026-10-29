# Diccionario de datos extendido

Este documento reúne cuatro formularios relacionados para describir los datos
del Sistema de Gestión de Gimnasio: flujos, estructuras, elementos y almacenes.
Complementa el resumen de tablas y campos de la sección 7.2 del DERCAS v1.3;
no lo reemplaza. El DERCAS conserva la vista breve del esquema y este documento
amplía el significado, la composición, el uso y las características de sus datos.

## Alcance y fuentes del modelo

El alcance corresponde al sistema documentado en el DERCAS v1.3 y sus
migraciones. Los flujos se cotejan con los DFD de contexto y nivel 0, versión
1.1. Las tablas y atributos se cotejan con el modelo físico PostgreSQL 1.1, la
migración de empleados V1_2 y la migración de intentos por gimnasio V1_3. Los
campos se presentan con el nombre lógico en español y el nombre físico usado
por PostgreSQL.

El diccionario distingue cuatro niveles:

- Un **flujo** nombra datos que entran, salen o se mueven entre procesos y
  almacenes en el DFD.
- Una **estructura** muestra los componentes que viajan juntos y las
  alternativas u opcionales mediante notación algebraica.
- Un **elemento** define una pieza de dato atómica, incluidos sus límites,
  valores y condiciones de uso.
- Un **almacén** describe dónde se conserva un conjunto persistente de datos y
  sus claves.

El DFD representa el flujo de trabajo del sistema; no implica que todos sus
datos se guarden. Por ejemplo, la contraseña digitada y un código de acceso
desconocido son transitorios. El sistema no guarda la contraseña en claro ni
el texto de un código desconocido. Cuando un intento no corresponde a un
código existente, `INTENTO_ACCESO.CODIGO_ACCESO_ID` queda sin valor.

## Convenciones de los formularios

En la composición de estructuras se usa `=` para definir, `+` para unir
componentes, `|` para alternativas excluyentes, `{ }` para repetición y
`( )` para un componente opcional. Esta notación describe la estructura lógica;
no es una expresión SQL.

Los tipos, longitudes, nulabilidad, valores iniciales y restricciones físicas
son los del esquema documentado. Cuando el proyecto no especifica un dato
operativo -como tamaño de bloque, cantidad promedio de filas o crecimiento
anual- se indica **No documentado**. No se infieren estadísticas de una base
de datos concreta.

En el formulario de elementos, **Base** significa que el dato se captura o se
recibe como valor. **Derivado** indica que se calcula a partir de otros datos.
Se agregan **Generado** para identificadores o valores creados por el sistema y
**Referencia** para claves que apuntan a otro registro. Es una adaptación para
distinguir los campos físicos del proyecto; no cambia el formato de definición
de elementos de Kendall y Kendall.

## Formulario 1. Descripción de flujos de datos

Cada registro documenta el nombre y descripción del flujo, su origen y destino,
el tipo de medio, la estructura que lo acompaña, su volumen o frecuencia y los
comentarios necesarios. **Pantalla** identifica la interacción de la interfaz;
**Interno / base de datos** identifica lecturas y escrituras entre procesos y
almacenes.

| ID | Nombre del flujo | Descripción | Origen → destino | Tipo | Estructura asociada | Volumen / tiempo | Comentarios |
|---|---|---|---|---|---|---|---|
| F01 | Credenciales de empleado | Datos que el empleado presenta para iniciar sesión. | Empleado → 6 Autenticar empleado | Pantalla / formulario | `CREDENCIALES_EMPLEADO` | Por inicio de sesión; no medido. | La contraseña sólo se usa durante la autenticación. |
| F02 | Resultado de autenticación y gimnasio asociado | Resultado del inicio de sesión y, si se acepta, contexto de la cuenta autenticada. | 6 Autenticar empleado → Empleado | Pantalla | `RESULTADO_AUTENTICACION` | Por inicio de sesión; no medido. | Si falla, el mensaje no distingue entre usuario, contraseña o estado inválido. |
| F03 | Datos del miembro | Datos personales capturados al registrar un miembro. | Empleado → 2 Registrar miembro | Pantalla / formulario | `DATOS_MIEMBRO` | Por registro; no medido. | Número de documento, teléfono y correo son opcionales. |
| F04 | Confirmación de registro | Resultado presentado al empleado después de registrar un miembro. | 2 Registrar miembro → Empleado | Pantalla | `CONFIRMACION_REGISTRO` | Por registro; no medido. | Incluye mensaje de éxito o de validación/error. |
| F05 | Datos del plan y membresía | Selección del miembro y plan, con la fecha de inicio elegida para asignar una membresía. | Empleado → 3 Gestionar membresía | Pantalla / formulario | `SOLICITUD_MEMBRESIA` | Por asignación; no medido. | La fecha final se calcula con la duración vigente del plan. |
| F06 | Código único generado | Código devuelto después de asignar correctamente la membresía. | 4 Generar código de acceso → Empleado | Pantalla | `CODIGO_GENERADO` | Por asignación exitosa; no medido. | El código se genera para la membresía y se almacena junto con ella en una transacción. |
| F07 | Código de acceso ingresado manualmente | Texto escrito por el empleado para validar una entrada. | Empleado → 5 Validar acceso y registrar intento | Pantalla / formulario | `CODIGO_INGRESADO` | Por validación; no medido. | Se recortan espacios exteriores. El texto desconocido no se conserva. |
| F08 | Resultado de acceso | Decisión y motivo que se presentan al empleado. | 5 Validar acceso y registrar intento → Empleado | Pantalla | `RESULTADO_ACCESO` | Por validación; no medido. | El resultado es autorizado o rechazado; el motivo se limita a 100 caracteres al registrarse. |
| F09 | Cuenta de empleado | Cuenta consultada para comprobar el usuario, su hash y el gimnasio asociado. | 6 Empleados → 6 Autenticar empleado | Interno / base de datos | `CUENTA_EMPLEADO` | Por inicio de sesión; no medido. | La cuenta debe estar activa y su gimnasio también. |
| F10 | Gimnasio asociado | Registro del gimnasio al que pertenece la cuenta autenticada. | 7 Gimnasios → 6 Autenticar empleado | Interno / base de datos | `GIMNASIO_ASOCIADO` | Por inicio de sesión; no medido. | La interfaz no permite escoger un gimnasio al iniciar sesión. |
| F11 | Datos del miembro registrado | Registro persistido al crear o actualizar la información de un miembro. | 2 Registrar miembro → 1 Miembros | Interno / base de datos | `MIEMBRO_REGISTRADO` | Por operación; no medido. | El `GIMNASIO_ID` proviene del contexto autenticado. |
| F12 | Datos del miembro registrado | Datos del miembro consultados para asignarle una membresía. | 1 Miembros → 3 Gestionar membresía | Interno / base de datos | `MIEMBRO_REGISTRADO` | Por consulta o carga de opciones; no medido. | Sólo se ofrecen miembros del gimnasio de la sesión. |
| F13 | Datos del plan | Planes activos consultados para la asignación. | 2 Planes → 3 Gestionar membresía | Interno / base de datos | `PLAN` | Por consulta o carga de opciones; no medido. | Sólo se ofrecen planes activos del gimnasio actual. |
| F14 | Membresía creada o actualizada | Registro de membresía insertado o cambio de estado guardado. | 3 Gestionar membresía → 3 Membresías | Interno / base de datos | `MEMBRESIA` | Por operación; no medido. | La asignación incluye gimnasio, miembro, plan, estado y fechas. Una membresía activa por miembro está protegida por una restricción única parcial. |
| F15 | Datos de la membresía | Identidad de la membresía recién creada que necesita la generación del código. | 3 Membresías → 4 Generar código de acceso | Interno / base de datos | `MEMBRESIA_PARA_CODIGO` | Por asignación exitosa; no medido. | La relación entre membresía y código se confirma en la misma transacción. |
| F16 | Código de acceso generado | Registro persistido del código asociado a una membresía. | 4 Generar código de acceso → 4 Códigos de acceso | Interno / base de datos | `CODIGO_ACCESO` | Por asignación exitosa; no medido. | Código único globalmente y sin espacios. |
| F17 | Código de acceso almacenado | Datos consultados para decidir si el código existe y está activo, incluyendo sus asociaciones. | 4 Códigos de acceso → 5 Validar acceso y registrar intento | Interno / base de datos | `CODIGO_PARA_VALIDACION` | Por validación; no medido. | El gimnasio del código se obtiene mediante su membresía, no de una columna directa de `CODIGO_ACCESO`. |
| F18 | Estado de la membresía | Estado y fechas de vigencia consultados para validar el código. | 3 Membresías → 5 Validar acceso y registrar intento | Interno / base de datos | `ESTADO_Y_VIGENCIA_MEMBRESIA` | Por validación; no medido. | La fecha final es vigente durante ese día; se rechaza cuando la fecha actual es posterior. |
| F19 | Intento de acceso registrado | Registro persistente del resultado de cada validación. | 5 Validar acceso y registrar intento → 5 Intentos de acceso | Interno / base de datos | `INTENTO_ACCESO` | Por validación; no medido. | Incluye el gimnasio de la sesión; la referencia al código es opcional. |
| F20 | Historial consultado | Intentos consultados para mostrar el historial del gimnasio actual. | 5 Intentos de acceso → 5 Validar acceso y registrar intento | Interno / base de datos | `HISTORIAL_INTENTOS` | Al abrir o actualizar el historial; no medido. | El historial se filtra por el gimnasio autenticado. |

## Formulario 2. Descripción de estructuras de datos

Cada ficha identifica la estructura, su descripción breve, composición y los
flujos relacionados. Las formas compuestas se expresan con la notación definida
arriba; cuando el dato deriva de una consulta o unión, se señala en los
comentarios y no se presenta como columna física adicional.

| Nombre | Descripción breve | Composición | Flujos / almacenes relacionados | Volumen / tiempo | Comentarios |
|---|---|---|---|---|---|
| `CREDENCIALES_EMPLEADO` | Valores usados para solicitar el inicio de sesión. | `USUARIO + CONTRASENA_INGRESADA` | F01 | Depende de los inicios de sesión; no medido. | La contraseña ingresada no es una columna de base de datos. |
| `RESULTADO_AUTENTICACION` | Resultado del login y datos de sesión sólo cuando se autentica. | `ESTADO_AUTENTICACION + [DATOS_SESION \| MENSAJE_GENERAL]` | F02 | Un resultado por inicio de sesión; no medido. | La aplicación usa el gimnasio asociado a la cuenta; no se acepta un gimnasio escogido desde la pantalla. |
| `DATOS_SESION` | Identidad del empleado y gimnasio resuelto para la sesión. | `EMPLEADO_ID + GIMNASIO_ID + NOMBRE_GIMNASIO` | F02 | Una estructura por sesión aceptada; no medido. | La sesión se construye sólo si cuenta y gimnasio están activos. |
| `DATOS_MIEMBRO` | Datos capturados para registrar o actualizar a un miembro. | `NOMBRES + APELLIDOS + (NUMERO_DOCUMENTO) + (TELEFONO) + (CORREO)` | F03, F11 | Depende de las operaciones de miembro; no medido. | El ID y el gimnasio se agregan al persistir, no se capturan como parte del formulario de persona. |
| `CONFIRMACION_REGISTRO` | Mensaje de resultado de la operación de miembro. | `RESULTADO_OPERACION + (NOMBRE_COMPLETO) + MENSAJE` | F04 | Una respuesta por operación; no medido. | Los textos son salida de interfaz, no campos de una tabla. |
| `SOLICITUD_MEMBRESIA` | Selección que el empleado envía para asignar una membresía. | `MIEMBRO_ID + PLAN_ID + FECHA_INICIO` | F05 | Depende de las asignaciones; no medido. | `FECHA_FIN` se calcula como fecha de inicio más duración del plan menos un día. |
| `MIEMBRO_REGISTRADO` | Registro persistente y datos del miembro en su gimnasio. | `MIEMBRO_ID + GIMNASIO_ID + DATOS_MIEMBRO + MIEMBRO_CREADO_EN` | F11, F12 | Una fila por miembro; cantidades no medidas. | La base aplica la unicidad del documento dentro del gimnasio cuando se proporciona. |
| `PLAN` | Información del plan ofrecido por un gimnasio. | `PLAN_ID + GIMNASIO_ID + NOMBRE_PLAN + DURACION_DIAS + PLAN_ACTIVO + PLAN_CREADO_EN` | F13 | Una fila por plan; cantidades no medidas. | Duración mínima: un día. Nombre único dentro del gimnasio. |
| `MEMBRESIA` | Asignación de un plan a un miembro en un gimnasio, con estado y vigencia. | `MEMBRESIA_ID + GIMNASIO_ID + MIEMBRO_ID + PLAN_ID + ESTADO_MEMBRESIA + FECHA_INICIO + FECHA_FIN + MEMBRESIA_CREADA_EN` | F14, F15, F18 | Una fila por asignación; cantidades no medidas. | La base comprueba que plan y miembro pertenezcan al mismo gimnasio. |
| `MEMBRESIA_PARA_CODIGO` | Referencia de la membresía para generar su código asociado. | `MEMBRESIA_ID + GIMNASIO_ID` | F15 | Una estructura por asignación exitosa; no medido. | `GIMNASIO_ID` se obtiene de la membresía para controlar el alcance; no se duplica en la tabla de códigos. |
| `CODIGO_ACCESO` | Código activo o inactivo enlazado a una membresía. | `CODIGO_ACCESO_ID + MEMBRESIA_ID + CODIGO + CODIGO_ACTIVO + CODIGO_CREADO_EN` | F06, F16 | Una fila por código generado; cantidades no medidas. | El valor de `CODIGO` es único globalmente. |
| `CODIGO_PARA_VALIDACION` | Código y datos de membresía necesarios para comprobar una entrada. | `CODIGO + CODIGO_ACTIVO + MEMBRESIA_ID + GIMNASIO_ID + ESTADO_MEMBRESIA + FECHA_INICIO + FECHA_FIN` | F17 | Una lectura por validación con código reconocido; no medido. | `GIMNASIO_ID`, estado y fechas son datos obtenidos por relación con membresía; no todos pertenecen a la tabla `CODIGO_ACCESO`. |
| `ESTADO_Y_VIGENCIA_MEMBRESIA` | Campos que determinan si la membresía permite el acceso en la fecha actual. | `ESTADO_MEMBRESIA + FECHA_INICIO + FECHA_FIN` | F18 | Una lectura por validación con código reconocido; no medido. | Estados permitidos: `ACTIVA`, `CONGELADA`, `VENCIDA`. |
| `CODIGO_GENERADO` | Valor de acceso que se devuelve al empleado tras asignar la membresía. | `CODIGO` | F06 | Un código por asignación exitosa; no medido. | El ID y estado del código se almacenan, pero el mensaje de pantalla devuelve el valor del código. |
| `CODIGO_INGRESADO` | Texto que el empleado intenta validar. | `CODIGO_INGRESADO` | F07 | Un dato por validación; no medido. | Se recortan espacios exteriores; si no se encuentra, el valor digitado no se persiste. |
| `RESULTADO_ACCESO` | Decisión de autorización y explicación para el empleado. | `RESULTADO_INTENTO + MOTIVO_INTENTO` | F08 | Una respuesta por validación; no medido. | Valores del resultado: `AUTORIZADO` o `RECHAZADO`. |
| `INTENTO_ACCESO` | Evidencia persistente del resultado de una validación. | `INTENTO_ACCESO_ID + GIMNASIO_ID + (CODIGO_ACCESO_ID) + RESULTADO_INTENTO + MOTIVO_INTENTO + FECHA_HORA_INTENTO` | F19, F20 | Un intento por validación procesada; cantidades no medidas. | La referencia a código puede faltar para códigos vacíos, desconocidos o ajenos al gimnasio. El texto digitado no se incluye. |
| `HISTORIAL_INTENTOS` | Conjunto de intentos mostrado al empleado. | `{INTENTO_ACCESO}` | F20 | A solicitud del empleado; no medido. | La consulta se limita al gimnasio de la sesión. |
| `CUENTA_EMPLEADO` | Datos de cuenta utilizados para autenticar a un empleado. | `EMPLEADO_ID + GIMNASIO_ID + USUARIO + CONTRASENA_HASH + EMPLEADO_ACTIVO + EMPLEADO_CREADO_EN` | F09 | Una consulta por inicio de sesión; no medido. | `CONTRASENA_HASH` contiene el hash, no la contraseña original. |
| `GIMNASIO_ASOCIADO` | Datos del gimnasio ligado a una cuenta de empleado. | `GIMNASIO_ID + NOMBRE_GIMNASIO + GIMNASIO_ACTIVO` | F10 | Una consulta por inicio de sesión; no medido. | Un empleado tiene asociado un único gimnasio. |
| `GIMNASIO_REGISTRADO` | Registro persistente completo del gimnasio. | `GIMNASIO_ID + NOMBRE_GIMNASIO + GIMNASIO_ACTIVO + GIMNASIO_CREADO_EN` | Almacén #7 | Una fila por gimnasio; cantidades no medidas. | Incluye la fecha de creación que no forma parte del flujo de autenticación `GIMNASIO_ASOCIADO`. |

## Formulario 3. Descripción de elementos de datos

El formulario sigue la ficha de elemento de Kendall y Kendall: **Nombre**,
**Alias**, **Descripción**, características (longitud, decimales, formato de
entrada, formato de salida y valor inicial), tipo (alfabético, alfanumérico,
fecha o numérico), **Base o derivado**, criterios de validación (límites
continuos o valores discretos y su significado) y comentarios. La tabla
siguiente completa esos campos para los elementos persistentes. El esquema no
define máscaras de captura ni presentación independientes, por lo que los
formatos de entrada y salida se registran como `No especificado` salvo que una
fila indique una regla del sistema; las fechas se muestran según la pantalla.

En todas las filas, **Decimales** es `No aplica` salvo indicación. Si una fila
no indica otro valor, sus formatos de entrada/salida y su valor inicial no
están especificados en el esquema. Los tipos `VARCHAR` son alfanuméricos;
`INT4` y `SERIAL` numéricos; `BOOL` booleanos; `DATE` fecha; `TIMESTAMP` fecha
y hora. `SERIAL` se declara como un entero con secuencia para generar
identificadores; cuando se usa como FK, el tipo físico es `INT4`. Cada elemento
se define una sola vez; la columna de uso enumera todas sus apariciones físicas.

### Elementos de identificación y referencia

| Nombre del elemento (alias; columna(s) física(s)) | Descripción | Características: tipo / longitud / decimales / formatos E-S | Clasificación | Validación, valores y predeterminado | Estructura / almacén |
|---|---|---|---|---|---|
| `GIMNASIO_ID` (ID de gimnasio; `GIMNASIO.GIMNASIO_ID`, `EMPLEADO.GIMNASIO_ID`, `PLAN.GIMNASIO_ID`, `MIEMBRO.GIMNASIO_ID`, `MEMBRESIA.GIMNASIO_ID`, `INTENTO_ACCESO.GIMNASIO_ID`) | Identificador del gimnasio y ámbito de propiedad de los registros multitenant. | `SERIAL` en GIMNASIO; `INT4` en referencias; longitud y decimales no aplican; formatos E-S no especificados. | Generado en GIMNASIO; Referencia en las demás tablas. | PK en GIMNASIO; FK obligatoria en empleado, plan, miembro, membresía e intento. Forma parte de las claves compuestas de membresía. | `GIMNASIO_ASOCIADO`, `CUENTA_EMPLEADO`, `PLAN`, `MIEMBRO_REGISTRADO`, `MEMBRESIA`, `INTENTO_ACCESO`; almacenes #1, #2, #3, #5, #6, #7. |
| `EMPLEADO_ID` (ID del empleado; `EMPLEADO.EMPLEADO_ID`) | Identificador de la cuenta de empleado. | `SERIAL`; longitud y decimales no aplican; formatos E-S no especificados. | Generado | PK; secuencia PostgreSQL. | `CUENTA_EMPLEADO`; EMPLEADOS (#6). |
| `PLAN_ID` (ID del plan; `PLAN.PLAN_ID`, `MEMBRESIA.PLAN_ID`) | Identificador del plan, usado también como referencia desde membresía. | `SERIAL` en PLAN; `INT4` en MEMBRESIA; longitud y decimales no aplican; formatos E-S no especificados. | Generado en PLAN; Referencia en MEMBRESIA. | PK en PLAN; FK compuesta con `GIMNASIO_ID` en MEMBRESIA. | `PLAN`, `SOLICITUD_MEMBRESIA`, `MEMBRESIA`; PLANES (#2), MEMBRESÍAS (#3). |
| `MIEMBRO_ID` (ID del miembro; `MIEMBRO.MIEMBRO_ID`, `MEMBRESIA.MIEMBRO_ID`) | Identificador del miembro, usado también como referencia desde membresía. | `SERIAL` en MIEMBRO; `INT4` en MEMBRESIA; longitud y decimales no aplican; formatos E-S no especificados. | Generado en MIEMBRO; Referencia en MEMBRESIA. | PK en MIEMBRO; FK compuesta con `GIMNASIO_ID` en MEMBRESIA. | `MIEMBRO_REGISTRADO`, `SOLICITUD_MEMBRESIA`, `MEMBRESIA`; MIEMBROS (#1), MEMBRESÍAS (#3). |
| `MEMBRESIA_ID` (ID de membresía; `MEMBRESIA.MEMBRESIA_ID`, `CODIGO_ACCESO.MEMBRESIA_ID`) | Identificador de membresía y vínculo de su código de acceso. | `SERIAL` en MEMBRESIA; `INT4` en CODIGO_ACCESO; longitud y decimales no aplican; formatos E-S no especificados. | Generado en MEMBRESIA; Referencia en CODIGO_ACCESO. | PK en MEMBRESIA; FK obligatoria en CODIGO_ACCESO. | `MEMBRESIA`, `MEMBRESIA_PARA_CODIGO`, `CODIGO_ACCESO`; MEMBRESÍAS (#3), CÓDIGOS DE ACCESO (#4). |
| `CODIGO_ACCESO_ID` (ID de código de acceso; `CODIGO_ACCESO.CODIGO_ACCESO_ID`, `INTENTO_ACCESO.CODIGO_ACCESO_ID`) | Identificador de un código reconocido; en intento es una referencia opcional. | `SERIAL` en CODIGO_ACCESO; `INT4` en INTENTO_ACCESO; longitud y decimales no aplican; formatos E-S no especificados. | Generado en CODIGO_ACCESO; Referencia en INTENTO_ACCESO. | PK en CODIGO_ACCESO; FK opcional en INTENTO_ACCESO. | `CODIGO_ACCESO`, `CODIGO_PARA_VALIDACION`, `INTENTO_ACCESO`; CÓDIGOS (#4), INTENTOS (#5). |
| `INTENTO_ACCESO_ID` (ID del intento; `INTENTO_ACCESO.INTENTO_ACCESO_ID`) | Identificador del intento registrado. | `SERIAL`; longitud y decimales no aplican; formatos E-S no especificados. | Generado | PK; secuencia PostgreSQL. | `INTENTO_ACCESO`, `HISTORIAL_INTENTOS`; INTENTOS DE ACCESO (#5). |

### Elementos propios del gimnasio y de empleados

| Nombre del elemento (alias; columna física) | Descripción | Características: tipo / longitud / decimales / formatos E-S | Clasificación | Validación, valores y predeterminado | Estructura / almacén |
|---|---|---|---|---|---|
| `NOMBRE_GIMNASIO` (Nombre del gimnasio; `GIMNASIO.NOMBRE`) | Nombre visible del gimnasio. | `VARCHAR(150)`; máximo 150; decimales no aplica; formatos E-S no especificados. | Base | Obligatorio y único globalmente. | `GIMNASIO_ASOCIADO`; GIMNASIOS (#7). |
| `GIMNASIO_ACTIVO` (Gimnasio activo; `GIMNASIO.GIMNASIO_ACTIVO`) | Indica si el gimnasio está habilitado. | `BOOL`; decimales no aplica; formatos E-S no especificados. | Base | Obligatorio; inicial `TRUE`; `TRUE`: habilitado, `FALSE`: inactivo. | `GIMNASIO_ASOCIADO`; GIMNASIOS (#7). |
| `GIMNASIO_CREADO_EN` (Fecha de creación del gimnasio; `GIMNASIO.GIMNASIO_CREADO_EN`) | Fecha y hora de alta del gimnasio. | `TIMESTAMP`; formatos E-S no especificados en el esquema. | Generado | Obligatorio; inicial `CURRENT_TIMESTAMP`. | `GIMNASIO_REGISTRADO`; GIMNASIOS (#7). |
| `USUARIO` (Usuario; `EMPLEADO.USUARIO`) | Nombre usado por el empleado para iniciar sesión. | `VARCHAR(100)`; longitud 3 a 100; formatos E-S no especificados. | Base | Obligatorio; único globalmente; se guarda en minúsculas, sin espacios exteriores, sólo `[a-z0-9._-]`. | `CUENTA_EMPLEADO`; EMPLEADOS (#6). |
| `CONTRASENA_HASH` (Hash de contraseña; `EMPLEADO.CONTRASENA_HASH`) | Representación criptográfica de la contraseña de la cuenta. | `VARCHAR(255)`; máximo 255; formatos E-S no especificados. | Derivado | Obligatorio; PBKDF2-HMAC-SHA-256 con sal aleatoria. Nunca contiene la contraseña en claro. | `CUENTA_EMPLEADO`; EMPLEADOS (#6). |
| `EMPLEADO_ACTIVO` (Empleado activo; `EMPLEADO.EMPLEADO_ACTIVO`) | Indica si la cuenta puede autenticarse. | `BOOL`; decimales no aplica; formatos E-S no especificados. | Base | Obligatorio; inicial `TRUE`; `TRUE`: habilitada, `FALSE`: no puede autenticarse. | `CUENTA_EMPLEADO`; EMPLEADOS (#6). |
| `EMPLEADO_CREADO_EN` (Fecha de creación del empleado; `EMPLEADO.EMPLEADO_CREADO_EN`) | Fecha y hora de alta de la cuenta. | `TIMESTAMP`; formatos E-S no especificados en el esquema. | Generado | Obligatorio; inicial `CURRENT_TIMESTAMP`. | `CUENTA_EMPLEADO`; EMPLEADOS (#6). |

### Elementos propios de planes y miembros

| Nombre del elemento (alias; columna física) | Descripción | Características: tipo / longitud / decimales / formatos E-S | Clasificación | Validación, valores y predeterminado | Estructura / almacén |
|---|---|---|---|---|---|
| `NOMBRE_PLAN` (Nombre del plan; `PLAN.NOMBRE`) | Nombre comercial del plan. | `VARCHAR(100)`; máximo 100; decimales no aplica; formatos E-S no especificados. | Base | Obligatorio; único dentro de `(GIMNASIO_ID, NOMBRE)`. | `PLAN`; PLANES (#2). |
| `DURACION_DIAS` (Duración en días; `PLAN.DURACION_DIAS`) | Número de días que aporta el plan a la membresía. | `INT4`; decimales no aplica; formatos E-S no especificados. | Base | Obligatorio; límite inferior 1 día. | `PLAN`, `SOLICITUD_MEMBRESIA`; PLANES (#2), MEMBRESÍAS (#3). |
| `PLAN_ACTIVO` (Plan activo; `PLAN.PLAN_ACTIVO`) | Indica si se puede ofrecer en nuevas asignaciones. | `BOOL`; decimales no aplica; formatos E-S no especificados. | Base | Obligatorio; inicial `TRUE`; `TRUE`: seleccionable, `FALSE`: no se ofrece en nuevas asignaciones. | `PLAN`; PLANES (#2). |
| `PLAN_CREADO_EN` (Fecha de creación del plan; `PLAN.PLAN_CREADO_EN`) | Fecha y hora de alta del plan. | `TIMESTAMP`; formatos E-S no especificados en el esquema. | Generado | Obligatorio; inicial `CURRENT_TIMESTAMP`. | `PLAN`; PLANES (#2). |
| `NOMBRES` (Nombres del miembro; `MIEMBRO.NOMBRES`) | Nombres de la persona registrada como miembro. | `VARCHAR(100)`; máximo 100; formatos E-S no especificados. | Base | Obligatorio. | `DATOS_MIEMBRO`, `MIEMBRO_REGISTRADO`; MIEMBROS (#1). |
| `APELLIDOS` (Apellidos del miembro; `MIEMBRO.APELLIDOS`) | Apellidos de la persona registrada como miembro. | `VARCHAR(100)`; máximo 100; formatos E-S no especificados. | Base | Obligatorio. | `DATOS_MIEMBRO`, `MIEMBRO_REGISTRADO`; MIEMBROS (#1). |
| `NUMERO_DOCUMENTO` (Número de documento; `MIEMBRO.NUMERO_DOCUMENTO`) | Documento de identificación, cuando se proporciona. | `VARCHAR(30)`; máximo 30; formatos E-S no especificados. | Base | Opcional; único por gimnasio cuando tiene valor. | `DATOS_MIEMBRO`, `MIEMBRO_REGISTRADO`; MIEMBROS (#1). |
| `TELEFONO` (Teléfono; `MIEMBRO.TELEFONO`) | Número de contacto del miembro. | `VARCHAR(25)`; máximo 25; formatos E-S no especificados. | Base | Opcional; no hay máscara adicional documentada. | `DATOS_MIEMBRO`, `MIEMBRO_REGISTRADO`; MIEMBROS (#1). |
| `CORREO` (Correo electrónico; `MIEMBRO.CORREO`) | Dirección de correo para contacto. | `VARCHAR(150)`; máximo 150; formatos E-S no especificados. | Base | Opcional; el esquema no documenta regla de formato adicional. | `DATOS_MIEMBRO`, `MIEMBRO_REGISTRADO`; MIEMBROS (#1). |
| `MIEMBRO_CREADO_EN` (Fecha de creación del miembro; `MIEMBRO.MIEMBRO_CREADO_EN`) | Fecha y hora de alta del miembro. | `TIMESTAMP`; formatos E-S no especificados en el esquema. | Generado | Obligatorio; inicial `CURRENT_TIMESTAMP`. | `MIEMBRO_REGISTRADO`; MIEMBROS (#1). |

### Elementos propios de membresías

| Nombre del elemento (alias; columna física) | Descripción | Características: tipo / longitud / decimales / formatos E-S | Clasificación | Validación, valores y predeterminado | Estructura / almacén |
|---|---|---|---|---|---|
| `ESTADO_MEMBRESIA` (Estado; `MEMBRESIA.ESTADO`) | Situación de la membresía. | `VARCHAR(20)`; máximo 20; decimales no aplica; formatos E-S no especificados. | Base | Obligatorio; `ACTIVA`: puede permitir acceso si código y vigencia también son válidos; `CONGELADA` o `VENCIDA`: acceso rechazado; inicial `ACTIVA`; se guarda en mayúsculas. | `MEMBRESIA`, `ESTADO_Y_VIGENCIA_MEMBRESIA`; MEMBRESÍAS (#3). |
| `FECHA_INICIO` (Fecha de inicio; `MEMBRESIA.FECHA_INICIO`) | Primer día de vigencia. | `DATE`; formatos E-S no especificados en el esquema. | Base | Obligatorio; sin default SQL. La pantalla inicia el selector con la fecha actual. | `SOLICITUD_MEMBRESIA`, `MEMBRESIA`, `ESTADO_Y_VIGENCIA_MEMBRESIA`; MEMBRESÍAS (#3). |
| `FECHA_FIN` (Fecha final; `MEMBRESIA.FECHA_FIN`) | Último día de vigencia. | `DATE`; formatos E-S no especificados en el esquema. | Derivado | Obligatorio; igual o posterior a `FECHA_INICIO`; inicio + duración del plan - 1 día. | `MEMBRESIA`, `ESTADO_Y_VIGENCIA_MEMBRESIA`; MEMBRESÍAS (#3). |
| `MEMBRESIA_CREADA_EN` (Fecha de creación de membresía; `MEMBRESIA.MEMBRESIA_CREADA_EN`) | Fecha y hora de creación del registro. | `TIMESTAMP`; formatos E-S no especificados en el esquema. | Generado | Obligatorio; inicial `CURRENT_TIMESTAMP`. | `MEMBRESIA`; MEMBRESÍAS (#3). |

### Elementos propios de códigos e intentos de acceso

| Nombre del elemento (alias; columna física) | Descripción | Características: tipo / longitud / decimales / formatos E-S | Clasificación | Validación, valores y predeterminado | Estructura / almacén |
|---|---|---|---|---|---|
| `CODIGO` (Código de acceso; `CODIGO_ACCESO.CODIGO`) | Valor único que se genera para validar acceso. | `VARCHAR(64)`; máximo 64; formatos E-S no especificados. | Generado | Obligatorio; único globalmente; no admite espacios. | `CODIGO_ACCESO`, `CODIGO_PARA_VALIDACION`; CÓDIGOS DE ACCESO (#4). |
| `CODIGO_ACTIVO` (Código activo; `CODIGO_ACCESO.CODIGO_ACTIVO`) | Indica si el código está habilitado. | `BOOL`; decimales no aplica; formatos E-S no especificados. | Base | Obligatorio; inicial `TRUE`; `TRUE`: habilitado, `FALSE`: rechazado en la validación. | `CODIGO_ACCESO`, `CODIGO_PARA_VALIDACION`; CÓDIGOS DE ACCESO (#4). |
| `CODIGO_CREADO_EN` (Fecha de creación del código; `CODIGO_ACCESO.CODIGO_CREADO_EN`) | Fecha y hora de generación. | `TIMESTAMP`; formatos E-S no especificados en el esquema. | Generado | Obligatorio; inicial `CURRENT_TIMESTAMP`. | `CODIGO_ACCESO`; CÓDIGOS DE ACCESO (#4). |
| `RESULTADO_INTENTO` (Resultado; `INTENTO_ACCESO.RESULTADO`) | Decisión registrada de la validación. | `VARCHAR(12)`; máximo 12; decimales no aplica; formatos E-S no especificados. | Derivado | Obligatorio; `AUTORIZADO`: se permite la entrada; `RECHAZADO`: no se permite; se guarda en mayúsculas. | `RESULTADO_ACCESO`, `INTENTO_ACCESO`; INTENTOS DE ACCESO (#5). |
| `MOTIVO_INTENTO` (Motivo; `INTENTO_ACCESO.MOTIVO`) | Explicación general de la decisión. | `VARCHAR(100)`; máximo 100; formatos E-S no especificados. | Derivado | Obligatorio; el texto guardado se limita a 100 caracteres. | `RESULTADO_ACCESO`, `INTENTO_ACCESO`; INTENTOS DE ACCESO (#5). |
| `FECHA_HORA_INTENTO` (Fecha y hora; `INTENTO_ACCESO.FECHA_HORA`) | Momento en que se registró el intento. | `TIMESTAMP`; formato de pantalla `dd/MM/yyyy HH:mm`; valor inicial `CURRENT_TIMESTAMP`. | Generado | Obligatorio. | `INTENTO_ACCESO`, `HISTORIAL_INTENTOS`; INTENTOS DE ACCESO (#5). |

### Elementos transitorios del DFD

Estos elementos se incluyen para describir los flujos, pero **no son columnas
persistentes** del modelo físico.

| Nombre | Descripción | Características | Clasificación | Validación / comentarios | Estructura / flujos |
|---|---|---|---|---|---|
| `CONTRASENA_INGRESADA` (Contraseña digitada) | Secreto que el empleado introduce para iniciar sesión. | Texto enmascarado en la interfaz; longitud máxima no fijada por el esquema. | Base transitoria | Se copia a un arreglo de caracteres para verificar y se limpia al terminar; no se registra ni almacena en claro. | `CREDENCIALES_EMPLEADO`; F01. |
| `CODIGO_INGRESADO` (Código escrito) | Texto presentado por el empleado para buscar un código. | Texto de interfaz; el esquema no fija la longitud del campo de pantalla. | Base transitoria | Se recortan espacios exteriores. El intento sólo conserva la FK si el código existe y se asocia al gimnasio actual; el texto desconocido no se guarda. | `CODIGO_INGRESADO`; F07. |
| `ESTADO_AUTENTICACION` (Resultado de login) | Indica si la autenticación permitió abrir la sesión. | Estado lógico de pantalla. | Derivado | No se persiste como columna; el error al usuario es genérico. | `RESULTADO_AUTENTICACION`; F02. |
| `MENSAJE_GENERAL` (Mensaje genérico) | Explicación mostrada cuando una operación o autenticación no se completa. | Texto de pantalla. | Derivado | No se persiste como columna en el modelo descrito. | `RESULTADO_AUTENTICACION`; F02. |
| `RESULTADO_OPERACION` (Resultado de registro) | Indica si se completó el registro de miembro. | Estado lógico de pantalla. | Derivado | No se persiste como columna; los detalles dependen del resultado de la validación/operación. | `CONFIRMACION_REGISTRO`; F04. |
| `NOMBRE_COMPLETO` (Nombre del miembro presentado) | Unión de nombres y apellidos que se usa en el mensaje de registro. | Texto de pantalla. | Derivado | Se compone de `NOMBRES` y `APELLIDOS`; no se persiste aparte. | `CONFIRMACION_REGISTRO`; F04. |
| `MENSAJE` (Mensaje de operación) | Texto de confirmación o explicación de validación que muestra la interfaz. | Texto de pantalla. | Derivado | No se persiste como columna en el modelo descrito. | `CONFIRMACION_REGISTRO`, `RESULTADO_ACCESO`; F04, F08. |

## Formulario 4. Descripción de almacenes de datos

El formulario documenta el identificador, nombre, alias, propósito, tipo de
medio, formato, características físicas disponibles, estructura, flujos de
entrada y salida, clave
primaria, claves secundarias y comentarios. En la lista, el identificador
numérico reproduce el DFD nivel 0 v1.1; no es una clave de PostgreSQL.
Cuando el DFD no muestra un flujo de escritura hacia un almacén, se indica
expresamente en lugar de inventar uno.

Todas las entradas son **computarizadas** y de formato **base de datos
relacional PostgreSQL**. PostgreSQL administra el tamaño de bloque. El tamaño
de fila efectivo depende de los datos y de la implementación; las cantidades
promedio/máxima y el crecimiento anual no están medidos en la documentación.

| ID DFD | Nombre / alias | Descripción | Tipo / formato | Estructura almacenada | Flujos de entrada y salida | Clave primaria | Claves secundarias e índices | Comentarios y características |
|---|---|---|---|---|---|---|---|---|
| 1 | Miembros / `MIEMBRO` | Datos personales de cada miembro, dentro de un gimnasio. | Computarizado / PostgreSQL | `MIEMBRO_REGISTRADO` | Entrada: F11. Salida: F12. | `MIEMBRO_ID` | Única por `(GIMNASIO_ID, NUMERO_DOCUMENTO)` cuando el documento tiene valor; índice `IX_MIEMBRO_GIMNASIO`; clave `(GIMNASIO_ID, MIEMBRO_ID)` para la relación compuesta. | `NUMERO_DOCUMENTO`, teléfono y correo son opcionales. El gimnasio propietario es obligatorio. Tamaño de registro, cantidad promedio/máxima y crecimiento anual: No documentado. |
| 2 | Planes / `PLAN` | Planes ofrecidos por cada gimnasio. | Computarizado / PostgreSQL | `PLAN` | Entrada: no representada en el DFD. Salida: F13. | `PLAN_ID` | Única por `(GIMNASIO_ID, NOMBRE)`; clave `(GIMNASIO_ID, PLAN_ID)` para la relación con membresía; índice `IX_PLAN_GIMNASIO`. | La duración mínima es 1 día. Tamaño de registro, cantidad promedio/máxima y crecimiento anual: No documentado. |
| 3 | Membresías / `MEMBRESIA` | Historial de asignaciones de planes a miembros. | Computarizado / PostgreSQL | `MEMBRESIA` | Entrada: F14. Salidas: F15 y F18. | `MEMBRESIA_ID` | Índice `IX_MEMBRESIA_GIMNASIO`, índice por miembro y plan; índice único parcial `UX_MEMBRESIA_MIEMBRO_ACTIVA` para permitir a lo sumo una membresía `ACTIVA` por miembro. | Las FK compuestas impiden asociar un plan o miembro de otro gimnasio. La fecha final no precede a la inicial. Tamaño de registro, cantidades y crecimiento: No documentado. |
| 4 | Códigos de acceso / `CODIGO_ACCESO` | Códigos generados para membresías. | Computarizado / PostgreSQL | `CODIGO_ACCESO` | Entrada: F16. Salida: F17. | `CODIGO_ACCESO_ID` | `CODIGO` único global; índice `TIENE_CODIGO_FK` sobre `MEMBRESIA_ID`. | No contiene `GIMNASIO_ID`; el gimnasio se deriva de la membresía. El código no admite espacios. Tamaño de registro, cantidades y crecimiento: No documentado. |
| 5 | Intentos de acceso / `INTENTO_ACCESO` | Historial de decisiones de acceso, incluido el intento sin código reconocido. | Computarizado / PostgreSQL | `INTENTO_ACCESO` | Entrada: F19. Salida: F20. | `INTENTO_ACCESO_ID` | Índice `IX_INTENTO_GIMNASIO_FECHA` sobre gimnasio, fecha descendente e ID descendente; índice por `CODIGO_ACCESO_ID`. | `CODIGO_ACCESO_ID` es opcional; `GIMNASIO_ID` es obligatorio. El código escrito desconocido no se almacena. Tamaño de registro, cantidades y crecimiento: No documentado. |
| 6 | Empleados / `EMPLEADO` | Cuentas para acceder al sistema, cada una asociada a un único gimnasio. | Computarizado / PostgreSQL | `CUENTA_EMPLEADO` | Entrada: no representada en el DFD. Salida: F09. | `EMPLEADO_ID` | `USUARIO` único globalmente; índice `IX_EMPLEADO_GIMNASIO`. | La contraseña original no se almacena; se conserva su hash. Tamaño de registro, cantidades y crecimiento: No documentado. |
| 7 | Gimnasios / `GIMNASIO` | Tenants propietarios de planes, miembros, empleados y membresías. | Computarizado / PostgreSQL | `GIMNASIO_REGISTRADO` | Entrada: no representada en el DFD. Salida: F10. | `GIMNASIO_ID` | `NOMBRE` único globalmente. | Es la raíz del aislamiento de datos. Tamaño de registro, cantidades y crecimiento: No documentado. |

## Referencias y trazabilidad

- Kendall, Kenneth E. y Kendall, Julie E. *Systems Analysis and Design*, 10.ª
  edición, Pearson Global Edition, 2019, capítulo 8, “Analyzing Systems Using Data
  Dictionaries”, pp. 255–278. [Vista previa editorial](https://api.pageplace.de/preview/DT0400.9781292281476_A39573239/preview-9781292281476_A39573239.pdf).
- [DERCAS del Sistema de Gestión de Gimnasio, versión 1.3](DERCAS-Sistema-Gimnasio-MVP-v1.3.pdf), secciones 7 y 7.2.
- DFD contexto y nivel 0, versión 1.1: [contexto](diagramas/dfd/DFD_Contexto_1.1.png) y [nivel 0](diagramas/dfd/DFD_Nivel_0_1.1.png).
- Modelo físico PostgreSQL: [DDL multitenant v1.1](diagramas/der/Sistema_Gimnasio_Modelo_Fisico_v1.1_multitenant.sql), junto con las migraciones [V1_2 de empleados](../database/migrations/V1_2__crear_empleados.sql) y [V1_3 de intentos por gimnasio](../database/migrations/V1_3__aislar_intentos_por_gimnasio.sql).

Los cuatro formularios adaptan la estructura de registro de Kendall y Kendall
al esquema relacional y al DFD del gimnasio. El formulario de flujo conserva
origen, destino, tipo, estructura y volumen/tiempo; el de estructura conserva
descripción, composición y flujos relacionados; el de elemento conserva
características, formatos y criterios de validación; y el de almacén conserva
identificación, características físicas, estructura, flujos y claves. Los nombres y
los ejemplos se han sustituido por los del sistema documentado.
