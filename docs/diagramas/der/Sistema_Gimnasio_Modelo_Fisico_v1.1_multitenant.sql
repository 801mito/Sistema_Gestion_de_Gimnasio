/* Modelo físico multitenant del Sistema de Gestión de Gimnasio, versión 1.1. */
/* Ejecutar sobre una base de datos PostgreSQL vacía. */

begin;

/*==============================================================*/
/* Table: GIMNASIO                                              */
/*==============================================================*/
create table GIMNASIO (
   GIMNASIO_ID          SERIAL               not null,
   NOMBRE               VARCHAR(150)         not null,
   GIMNASIO_ACTIVO      BOOL                 not null default TRUE,
   GIMNASIO_CREADO_EN   TIMESTAMP            not null default CURRENT_TIMESTAMP,
   constraint PK_GIMNASIO primary key (GIMNASIO_ID),
   constraint AK_GIMNASIO_NOMBRE unique (NOMBRE)
);

/*==============================================================*/
/* Table: PLAN                                                  */
/*==============================================================*/
create table PLAN (
   PLAN_ID              SERIAL               not null,
   GIMNASIO_ID          INT4                 not null,
   NOMBRE               VARCHAR(100)         not null,
   DURACION_DIAS        INT4                 not null
      constraint CKC_DURACION_DIAS_PLAN check (DURACION_DIAS >= 1),
   PLAN_ACTIVO          BOOL                 not null default TRUE,
   PLAN_CREADO_EN       TIMESTAMP            not null default CURRENT_TIMESTAMP,
   constraint PK_PLAN primary key (PLAN_ID),
   constraint AK_PLAN_GIMNASIO_NOMBRE unique (GIMNASIO_ID, NOMBRE),
   constraint AK_PLAN_GIMNASIO_ID unique (GIMNASIO_ID, PLAN_ID)
);

create index IX_PLAN_GIMNASIO on PLAN (GIMNASIO_ID);

/*==============================================================*/
/* Table: MIEMBRO                                               */
/*==============================================================*/
create table MIEMBRO (
   MIEMBRO_ID           SERIAL               not null,
   GIMNASIO_ID          INT4                 not null,
   NOMBRES              VARCHAR(100)         not null,
   APELLIDOS            VARCHAR(100)         not null,
   NUMERO_DOCUMENTO     VARCHAR(30)          null,
   TELEFONO             VARCHAR(25)          null,
   CORREO               VARCHAR(150)         null,
   MIEMBRO_CREADO_EN    TIMESTAMP            not null default CURRENT_TIMESTAMP,
   constraint PK_MIEMBRO primary key (MIEMBRO_ID),
   constraint AK_MIEMBRO_GIMNASIO_DOCUMENTO unique (GIMNASIO_ID, NUMERO_DOCUMENTO),
   constraint AK_MIEMBRO_GIMNASIO_ID unique (GIMNASIO_ID, MIEMBRO_ID)
);

create index IX_MIEMBRO_GIMNASIO on MIEMBRO (GIMNASIO_ID);

/*==============================================================*/
/* Table: MEMBRESIA                                             */
/*==============================================================*/
create table MEMBRESIA (
   MEMBRESIA_ID         SERIAL               not null,
   GIMNASIO_ID          INT4                 not null,
   PLAN_ID              INT4                 not null,
   MIEMBRO_ID           INT4                 not null,
   ESTADO               VARCHAR(20)          not null default 'ACTIVA'
      constraint CKC_ESTADO_MEMBRESI check (ESTADO in ('ACTIVA','CONGELADA','VENCIDA') and ESTADO = upper(ESTADO)),
   FECHA_INICIO         DATE                 not null,
   FECHA_FIN            DATE                 not null,
   MEMBRESIA_CREADA_EN  TIMESTAMP            not null default CURRENT_TIMESTAMP,
   constraint PK_MEMBRESIA primary key (MEMBRESIA_ID),
   constraint RN_FECHAS_MEMBRESIA check (FECHA_FIN >= FECHA_INICIO)
);

create index IX_MEMBRESIA_GIMNASIO on MEMBRESIA (GIMNASIO_ID);
create index TIENE_MEMBRESIA_FK on MEMBRESIA (MIEMBRO_ID);
create index SE_ASIGNA_A_FK on MEMBRESIA (PLAN_ID);

/* Un miembro puede tener varias membresías históricas, pero solo una activa. */
create unique index UX_MEMBRESIA_MIEMBRO_ACTIVA
on MEMBRESIA (MIEMBRO_ID)
where ESTADO = 'ACTIVA';

/*==============================================================*/
/* Table: CODIGO_ACCESO                                         */
/*==============================================================*/
create table CODIGO_ACCESO (
   CODIGO_ACCESO_ID     SERIAL               not null,
   MEMBRESIA_ID         INT4                 not null,
   CODIGO               VARCHAR(64)          not null,
   CODIGO_ACTIVO        BOOL                 not null default TRUE,
   CODIGO_CREADO_EN     TIMESTAMP            not null default CURRENT_TIMESTAMP,
   constraint PK_CODIGO_ACCESO primary key (CODIGO_ACCESO_ID),
   constraint AK_AK_CODIGO_ACCESO_CODIGO_A unique (CODIGO),
   constraint CK_CODIGO_SIN_ESPACIOS check (CODIGO !~ '[[:space:]]')
);

create index TIENE_CODIGO_FK on CODIGO_ACCESO (MEMBRESIA_ID);

/*==============================================================*/
/* Table: INTENTO_ACCESO                                        */
/*==============================================================*/
create table INTENTO_ACCESO (
   INTENTO_ACCESO_ID    SERIAL               not null,
   CODIGO_ACCESO_ID     INT4                 null,
   RESULTADO            VARCHAR(12)          not null
      constraint CKC_RESULTADO_INTENTO_ check (RESULTADO in ('AUTORIZADO','RECHAZADO') and RESULTADO = upper(RESULTADO)),
   MOTIVO               VARCHAR(100)         not null,
   FECHA_HORA           TIMESTAMP            not null default CURRENT_TIMESTAMP,
   constraint PK_INTENTO_ACCESO primary key (INTENTO_ACCESO_ID)
);

create index SE_UTILIZA_EN_FK on INTENTO_ACCESO (CODIGO_ACCESO_ID);

/*==============================================================*/
/* Relaciones                                                   */
/*==============================================================*/
alter table PLAN
   add constraint FK_PLAN_PERTENECE_GIMNASIO foreign key (GIMNASIO_ID)
      references GIMNASIO (GIMNASIO_ID)
      on delete restrict on update restrict;

alter table MIEMBRO
   add constraint FK_MIEMBRO_PERTENECE_GIMNASIO foreign key (GIMNASIO_ID)
      references GIMNASIO (GIMNASIO_ID)
      on delete restrict on update restrict;

alter table MEMBRESIA
   add constraint FK_MEMBRESIA_PERTENECE_GIMNASIO foreign key (GIMNASIO_ID)
      references GIMNASIO (GIMNASIO_ID)
      on delete restrict on update restrict;

/* Las claves compuestas garantizan que plan y miembro sean del mismo gimnasio. */
alter table MEMBRESIA
   add constraint FK_MEMBRESIA_PLAN_GIMNASIO foreign key (GIMNASIO_ID, PLAN_ID)
      references PLAN (GIMNASIO_ID, PLAN_ID)
      on delete restrict on update restrict;

alter table MEMBRESIA
   add constraint FK_MEMBRESIA_MIEMBRO_GIMNASIO foreign key (GIMNASIO_ID, MIEMBRO_ID)
      references MIEMBRO (GIMNASIO_ID, MIEMBRO_ID)
      on delete restrict on update restrict;

alter table CODIGO_ACCESO
   add constraint FK_CODIGO_A_TIENE_COD_MEMBRESI foreign key (MEMBRESIA_ID)
      references MEMBRESIA (MEMBRESIA_ID)
      on delete restrict on update restrict;

alter table INTENTO_ACCESO
   add constraint FK_INTENTO__SE_UTILIZ_CODIGO_A foreign key (CODIGO_ACCESO_ID)
      references CODIGO_ACCESO (CODIGO_ACCESO_ID)
      on delete restrict on update restrict;

commit;
