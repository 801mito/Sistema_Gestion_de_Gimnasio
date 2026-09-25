/*
 * Migración hacia el modelo multitenant versión 1.1.
 *
 * Ejecutar una sola vez sobre la base de datos existente. La migración crea
 * el gimnasio inicial "Gimnasio Principal" y asocia a él los datos actuales.
 * Ejecutar después de realizar un respaldo de la base de datos.
 */

begin;

create table if not exists GIMNASIO (
   GIMNASIO_ID          SERIAL               not null,
   NOMBRE               VARCHAR(150)         not null,
   GIMNASIO_ACTIVO      BOOL                 not null default TRUE,
   GIMNASIO_CREADO_EN   TIMESTAMP            not null default CURRENT_TIMESTAMP,
   constraint PK_GIMNASIO primary key (GIMNASIO_ID),
   constraint AK_GIMNASIO_NOMBRE unique (NOMBRE)
);

insert into GIMNASIO (NOMBRE)
values ('Gimnasio Principal')
on conflict (NOMBRE) do nothing;

alter table PLAN add column if not exists GIMNASIO_ID INT4;
alter table MIEMBRO add column if not exists GIMNASIO_ID INT4;
alter table MEMBRESIA add column if not exists GIMNASIO_ID INT4;

do $$
declare
   v_gimnasio_id integer;
begin
   select GIMNASIO_ID
   into v_gimnasio_id
   from GIMNASIO
   where NOMBRE = 'Gimnasio Principal';

   if v_gimnasio_id is null then
      raise exception 'No fue posible obtener el gimnasio inicial.';
   end if;

   update PLAN
   set GIMNASIO_ID = v_gimnasio_id
   where GIMNASIO_ID is null;

   update MIEMBRO
   set GIMNASIO_ID = v_gimnasio_id
   where GIMNASIO_ID is null;

   update MEMBRESIA
   set GIMNASIO_ID = v_gimnasio_id
   where GIMNASIO_ID is null;
end
$$;

alter table PLAN alter column GIMNASIO_ID set not null;
alter table MIEMBRO alter column GIMNASIO_ID set not null;
alter table MEMBRESIA alter column GIMNASIO_ID set not null;

/* La unicidad ahora se valida dentro de cada gimnasio. */
alter table PLAN drop constraint if exists AK_AK_PLAN_NOMBRE_PLAN;
drop index if exists UX_MIEMBRO_NUMERO_DOCUMENTO;

create unique index if not exists UX_PLAN_GIMNASIO_NOMBRE
on PLAN (GIMNASIO_ID, NOMBRE);

create unique index if not exists UX_MIEMBRO_GIMNASIO_DOCUMENTO
on MIEMBRO (GIMNASIO_ID, NUMERO_DOCUMENTO);

/* Estas claves candidatas permiten las relaciones compuestas de MEMBRESIA. */
create unique index if not exists AK_PLAN_GIMNASIO_ID
on PLAN (GIMNASIO_ID, PLAN_ID);

create unique index if not exists AK_MIEMBRO_GIMNASIO_ID
on MIEMBRO (GIMNASIO_ID, MIEMBRO_ID);

create index if not exists IX_PLAN_GIMNASIO on PLAN (GIMNASIO_ID);
create index if not exists IX_MIEMBRO_GIMNASIO on MIEMBRO (GIMNASIO_ID);
create index if not exists IX_MEMBRESIA_GIMNASIO on MEMBRESIA (GIMNASIO_ID);

alter table MEMBRESIA drop constraint if exists FK_MEMBRESI_SE_ASIGNA_PLAN;
alter table MEMBRESIA drop constraint if exists FK_MEMBRESI_TIENE_MEM_MIEMBRO;

do $$
begin
   if not exists (
      select 1 from pg_constraint
      where conname = 'fk_plan_pertenece_gimnasio'
        and conrelid = 'plan'::regclass
   ) then
      alter table PLAN
         add constraint FK_PLAN_PERTENECE_GIMNASIO foreign key (GIMNASIO_ID)
            references GIMNASIO (GIMNASIO_ID)
            on delete restrict on update restrict;
   end if;

   if not exists (
      select 1 from pg_constraint
      where conname = 'fk_miembro_pertenece_gimnasio'
        and conrelid = 'miembro'::regclass
   ) then
      alter table MIEMBRO
         add constraint FK_MIEMBRO_PERTENECE_GIMNASIO foreign key (GIMNASIO_ID)
            references GIMNASIO (GIMNASIO_ID)
            on delete restrict on update restrict;
   end if;

   if not exists (
      select 1 from pg_constraint
      where conname = 'fk_membresia_pertenece_gimnasio'
        and conrelid = 'membresia'::regclass
   ) then
      alter table MEMBRESIA
         add constraint FK_MEMBRESIA_PERTENECE_GIMNASIO foreign key (GIMNASIO_ID)
            references GIMNASIO (GIMNASIO_ID)
            on delete restrict on update restrict;
   end if;

   if not exists (
      select 1 from pg_constraint
      where conname = 'fk_membresia_plan_gimnasio'
        and conrelid = 'membresia'::regclass
   ) then
      alter table MEMBRESIA
         add constraint FK_MEMBRESIA_PLAN_GIMNASIO foreign key (GIMNASIO_ID, PLAN_ID)
            references PLAN (GIMNASIO_ID, PLAN_ID)
            on delete restrict on update restrict;
   end if;

   if not exists (
      select 1 from pg_constraint
      where conname = 'fk_membresia_miembro_gimnasio'
        and conrelid = 'membresia'::regclass
   ) then
      alter table MEMBRESIA
         add constraint FK_MEMBRESIA_MIEMBRO_GIMNASIO foreign key (GIMNASIO_ID, MIEMBRO_ID)
            references MIEMBRO (GIMNASIO_ID, MIEMBRO_ID)
            on delete restrict on update restrict;
   end if;
end
$$;

commit;
