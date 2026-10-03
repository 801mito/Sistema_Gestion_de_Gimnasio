/*
 * Cuentas de empleados para el futuro inicio de sesión.
 * Ejecutar una sola vez después de V1_1, con respaldo previo.
 * No crea usuarios ni contraseñas predeterminadas.
 */
begin;

create table EMPLEADO (
   EMPLEADO_ID         SERIAL          not null,
   GIMNASIO_ID         INT4            not null,
   USUARIO             VARCHAR(100)    not null,
   CONTRASENA_HASH     VARCHAR(255)    not null,
   EMPLEADO_ACTIVO     BOOL            not null default TRUE,
   EMPLEADO_CREADO_EN  TIMESTAMP       not null default CURRENT_TIMESTAMP,
   constraint PK_EMPLEADO primary key (EMPLEADO_ID),
   constraint AK_EMPLEADO_USUARIO unique (USUARIO),
   constraint CK_EMPLEADO_USUARIO check (
      length(USUARIO) between 3 and 100
      and USUARIO = lower(btrim(USUARIO))
      and USUARIO ~ '^[a-z0-9._-]+$'
   ),
   constraint FK_EMPLEADO_GIMNASIO foreign key (GIMNASIO_ID)
      references GIMNASIO (GIMNASIO_ID)
      on delete restrict on update restrict
);

create index IX_EMPLEADO_GIMNASIO on EMPLEADO (GIMNASIO_ID);

commit;
