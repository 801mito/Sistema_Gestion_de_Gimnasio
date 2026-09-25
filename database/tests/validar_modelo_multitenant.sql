/*
 * Pruebas del modelo multitenant versión 1.1.
 *
 * Ejecutar únicamente después de aplicar la migración
 * V1_1__preparar_modelo_multitenant.sql.
 * Todas las inserciones de prueba terminan con ROLLBACK.
 */

begin;

do $$
declare
   v_gimnasio_principal_id integer;
   v_gimnasio_secundario_id integer;
   v_plan_principal_id integer;
   v_plan_secundario_id integer;
   v_miembro_principal_id integer;
   v_miembro_secundario_id integer;
   v_membresia_id integer;
begin
   select GIMNASIO_ID
   into v_gimnasio_principal_id
   from GIMNASIO
   where NOMBRE = 'Gimnasio Principal';

   if v_gimnasio_principal_id is null then
      raise exception 'FALLO: no existe el gimnasio inicial';
   end if;

   if not exists (
      select 1
      from GIMNASIO
      where GIMNASIO_ID = v_gimnasio_principal_id
        and GIMNASIO_ACTIVO is true
        and GIMNASIO_CREADO_EN is not null
   ) then
      raise exception 'FALLO: valores predeterminados de GIMNASIO';
   end if;
   raise notice 'OK: gimnasio inicial y valores predeterminados';

   if exists (
      select 1 from PLAN where GIMNASIO_ID is null
      union all
      select 1 from MIEMBRO where GIMNASIO_ID is null
      union all
      select 1 from MEMBRESIA where GIMNASIO_ID is null
   ) then
      raise exception 'FALLO: existen registros migrados sin gimnasio';
   end if;
   raise notice 'OK: todos los registros existentes tienen gimnasio';

   if exists (
      select 1
      from MEMBRESIA m
      inner join PLAN p on p.PLAN_ID = m.PLAN_ID
      inner join MIEMBRO mi on mi.MIEMBRO_ID = m.MIEMBRO_ID
      where m.GIMNASIO_ID <> p.GIMNASIO_ID
         or m.GIMNASIO_ID <> mi.GIMNASIO_ID
   ) then
      raise exception 'FALLO: existen membresías con datos de distintos gimnasios';
   end if;
   raise notice 'OK: membresías existentes conservan relaciones del mismo gimnasio';

   insert into GIMNASIO (NOMBRE)
   values ('__PRUEBA_GIMNASIO_SECUNDARIO__')
   returning GIMNASIO_ID into v_gimnasio_secundario_id;

   insert into PLAN (GIMNASIO_ID, NOMBRE, DURACION_DIAS)
   values (v_gimnasio_principal_id, '__PRUEBA_PLAN_COMPARTIDO__', 30)
   returning PLAN_ID into v_plan_principal_id;

   insert into PLAN (GIMNASIO_ID, NOMBRE, DURACION_DIAS)
   values (v_gimnasio_secundario_id, '__PRUEBA_PLAN_COMPARTIDO__', 15)
   returning PLAN_ID into v_plan_secundario_id;
   raise notice 'OK: el mismo nombre de plan se permite en gimnasios distintos';

   begin
      insert into PLAN (GIMNASIO_ID, NOMBRE, DURACION_DIAS)
      values (v_gimnasio_principal_id, '__PRUEBA_PLAN_COMPARTIDO__', 60);
      raise exception 'FALLO: se permitió repetir un plan dentro del mismo gimnasio';
   exception
      when unique_violation then
         raise notice 'OK: nombre de plan duplicado rechazado dentro del mismo gimnasio';
   end;

   insert into MIEMBRO (GIMNASIO_ID, NOMBRES, APELLIDOS, NUMERO_DOCUMENTO)
   values (
      v_gimnasio_principal_id,
      'Miembro',
      'Principal',
      '__PRUEBA_DOCUMENTO_MULTITENANT__'
   )
   returning MIEMBRO_ID into v_miembro_principal_id;

   insert into MIEMBRO (GIMNASIO_ID, NOMBRES, APELLIDOS, NUMERO_DOCUMENTO)
   values (
      v_gimnasio_secundario_id,
      'Miembro',
      'Secundario',
      '__PRUEBA_DOCUMENTO_MULTITENANT__'
   )
   returning MIEMBRO_ID into v_miembro_secundario_id;
   raise notice 'OK: el mismo documento se permite en gimnasios distintos';

   begin
      insert into MIEMBRO (GIMNASIO_ID, NOMBRES, APELLIDOS, NUMERO_DOCUMENTO)
      values (
         v_gimnasio_principal_id,
         'Miembro',
         'Duplicado',
         '__PRUEBA_DOCUMENTO_MULTITENANT__'
      );
      raise exception 'FALLO: se permitió repetir un documento dentro del mismo gimnasio';
   exception
      when unique_violation then
         raise notice 'OK: documento duplicado rechazado dentro del mismo gimnasio';
   end;

   begin
      insert into MEMBRESIA (
         GIMNASIO_ID, PLAN_ID, MIEMBRO_ID, FECHA_INICIO, FECHA_FIN
      ) values (
         v_gimnasio_secundario_id,
         v_plan_principal_id,
         v_miembro_secundario_id,
         date '2026-09-01',
         date '2026-09-30'
      );
      raise exception 'FALLO: se permitió utilizar un plan de otro gimnasio';
   exception
      when foreign_key_violation then
         raise notice 'OK: plan de otro gimnasio rechazado';
   end;

   begin
      insert into MEMBRESIA (
         GIMNASIO_ID, PLAN_ID, MIEMBRO_ID, FECHA_INICIO, FECHA_FIN
      ) values (
         v_gimnasio_secundario_id,
         v_plan_secundario_id,
         v_miembro_principal_id,
         date '2026-09-01',
         date '2026-09-30'
      );
      raise exception 'FALLO: se permitió utilizar un miembro de otro gimnasio';
   exception
      when foreign_key_violation then
         raise notice 'OK: miembro de otro gimnasio rechazado';
   end;

   insert into MEMBRESIA (
      GIMNASIO_ID, PLAN_ID, MIEMBRO_ID, FECHA_INICIO, FECHA_FIN
   ) values (
      v_gimnasio_secundario_id,
      v_plan_secundario_id,
      v_miembro_secundario_id,
      date '2026-09-01',
      date '2026-09-30'
   )
   returning MEMBRESIA_ID into v_membresia_id;

   if v_membresia_id is null then
      raise exception 'FALLO: no fue posible crear una membresía del mismo gimnasio';
   end if;
   raise notice 'OK: membresía válida creada con datos del mismo gimnasio';

   raise notice 'Todas las pruebas multitenant finalizaron correctamente';
end
$$ language plpgsql;

/* Elimina los datos ficticios y conserva intactos los datos migrados. */
rollback;
