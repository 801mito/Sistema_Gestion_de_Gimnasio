-- Ejecutar una vez tras V1_2. Los intentos sin código del modelo previo
-- pertenecen al único gimnasio que existía antes de esta migración.
begin;

alter table intento_acceso add column gimnasio_id integer;

update intento_acceso intento
set gimnasio_id = membresia.gimnasio_id
from codigo_acceso codigo
join membresia on membresia.membresia_id = codigo.membresia_id
where intento.codigo_acceso_id = codigo.codigo_acceso_id;

update intento_acceso
set gimnasio_id = (select gimnasio_id from gimnasio where nombre = 'Gimnasio Principal')
where gimnasio_id is null;

alter table intento_acceso alter column gimnasio_id set not null;
alter table intento_acceso
   add constraint fk_intento_gimnasio foreign key (gimnasio_id)
   references gimnasio (gimnasio_id) on delete restrict on update restrict;

create index ix_intento_gimnasio_fecha
   on intento_acceso (gimnasio_id, fecha_hora desc, intento_acceso_id desc);

commit;
