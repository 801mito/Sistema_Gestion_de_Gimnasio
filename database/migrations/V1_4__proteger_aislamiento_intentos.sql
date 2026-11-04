-- Aplicar una vez cuando intento_acceso ya tenga gimnasio_id.
-- No reescribe los intentos existentes; la consulta oculta asociaciones ajenas.
begin;

-- Las funciones deben consultar las tablas de su propio esquema, incluso
-- cuando quien escribe use un search_path distinto o tenga tablas temporales.
select pg_catalog.set_config('search_path', pg_catalog.format('%I, pg_temp', current_schema()), true);

create function validar_gimnasio_codigo_intento() returns trigger
language plpgsql set search_path from current as $$
declare
   gimnasio_del_codigo integer;
begin
   if new.codigo_acceso_id is null then
      return new;
   end if;
   if tg_op = 'UPDATE' then
      if new.gimnasio_id is not distinct from old.gimnasio_id
         and new.codigo_acceso_id is not distinct from old.codigo_acceso_id then
         return new;
      end if;
   end if;

   select membresia.gimnasio_id into gimnasio_del_codigo
   from codigo_acceso codigo
   join membresia on membresia.membresia_id = codigo.membresia_id
   where codigo.codigo_acceso_id = new.codigo_acceso_id
   for key share of codigo;

   if not found then
      raise foreign_key_violation using message = 'El código del intento no existe.';
   end if;
   if gimnasio_del_codigo is distinct from new.gimnasio_id then
      raise check_violation using message = 'El código y el intento deben pertenecer al mismo gimnasio.';
   end if;
   return new;
end;
$$;

create trigger tr_intento_gimnasio_codigo
before insert or update of gimnasio_id, codigo_acceso_id on intento_acceso
for each row execute function validar_gimnasio_codigo_intento();

-- Las rutas normales de edición no cambian el dueño de una membresía.
-- Proteger los padres evita introducir un cruce sin modificar el intento.
create function impedir_traslado_membresia() returns trigger
language plpgsql set search_path from current as $$
begin
   raise check_violation using message = 'No se puede trasladar una membresía a otro gimnasio.';
end;
$$;

create trigger tr_membresia_gimnasio_inmutable
before update on membresia
for each row when (new.gimnasio_id is distinct from old.gimnasio_id)
execute function impedir_traslado_membresia();

create function impedir_traslado_codigo() returns trigger
language plpgsql set search_path from current as $$
declare
   gimnasio_anterior integer;
   gimnasio_nuevo integer;
begin
   select gimnasio_id into gimnasio_anterior from membresia where membresia_id = old.membresia_id;
   select gimnasio_id into gimnasio_nuevo
   from membresia where membresia_id = new.membresia_id
   for key share;
   if not found then
      raise foreign_key_violation using message = 'La nueva membresía del código no existe.';
   end if;
   if gimnasio_nuevo is distinct from gimnasio_anterior then
      raise check_violation using message = 'No se puede trasladar un código a otro gimnasio.';
   end if;
   return new;
end;
$$;

create trigger tr_codigo_gimnasio_inmutable
before update on codigo_acceso
for each row when (new.membresia_id is distinct from old.membresia_id)
execute function impedir_traslado_codigo();

commit;
