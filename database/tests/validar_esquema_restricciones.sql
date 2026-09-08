/*
 * Pruebas del esquema del Sistema de Gestión de Gimnasio.
 * Compatible con PostgreSQL 16.
 *
 * Todas las pruebas se ejecutan dentro de una transacción que termina
 * con ROLLBACK, por lo que no dejan datos ficticios almacenados.
 */

BEGIN;

DO $$
DECLARE
   v_plan_id             INTEGER;
   v_miembro_id          INTEGER;
   v_membresia_id        INTEGER;
   v_codigo_acceso_id    INTEGER;
   v_intento_acceso_id   INTEGER;
BEGIN
   RAISE NOTICE 'Inicio de las pruebas del esquema';

   /*============================================================*/
   /* 1. Inserciones válidas y valores predeterminados            */
   /*============================================================*/
   INSERT INTO PLAN (NOMBRE, DURACION_DIAS)
   VALUES ('__PRUEBA_PLAN_30_DIAS__', 30)
   RETURNING PLAN_ID INTO v_plan_id;

   IF v_plan_id IS NULL OR NOT EXISTS (
      SELECT 1
      FROM PLAN
      WHERE PLAN_ID = v_plan_id
        AND PLAN_ACTIVO IS TRUE
        AND PLAN_CREADO_EN IS NOT NULL
   ) THEN
      RAISE EXCEPTION 'FALLO: identificador o valores predeterminados de PLAN';
   END IF;
   RAISE NOTICE 'OK: inserción y valores predeterminados de PLAN';

   INSERT INTO MIEMBRO (NOMBRES, APELLIDOS, NUMERO_DOCUMENTO)
   VALUES ('Miembro', 'De Prueba', '__PRUEBA_DOCUMENTO_001__')
   RETURNING MIEMBRO_ID INTO v_miembro_id;

   IF v_miembro_id IS NULL OR NOT EXISTS (
      SELECT 1
      FROM MIEMBRO
      WHERE MIEMBRO_ID = v_miembro_id
        AND MIEMBRO_CREADO_EN IS NOT NULL
   ) THEN
      RAISE EXCEPTION 'FALLO: identificador o fecha predeterminada de MIEMBRO';
   END IF;
   RAISE NOTICE 'OK: inserción y valores predeterminados de MIEMBRO';

   INSERT INTO MEMBRESIA (PLAN_ID, MIEMBRO_ID, FECHA_INICIO, FECHA_FIN)
   VALUES (v_plan_id, v_miembro_id, DATE '2026-09-01', DATE '2026-09-30')
   RETURNING MEMBRESIA_ID INTO v_membresia_id;

   IF v_membresia_id IS NULL OR NOT EXISTS (
      SELECT 1
      FROM MEMBRESIA
      WHERE MEMBRESIA_ID = v_membresia_id
        AND ESTADO = 'ACTIVA'
        AND MEMBRESIA_CREADA_EN IS NOT NULL
   ) THEN
      RAISE EXCEPTION 'FALLO: identificador o valores predeterminados de MEMBRESIA';
   END IF;
   RAISE NOTICE 'OK: inserción y valores predeterminados de MEMBRESIA';

   INSERT INTO CODIGO_ACCESO (MEMBRESIA_ID, CODIGO)
   VALUES (v_membresia_id, '__PRUEBA_CODIGO_001__')
   RETURNING CODIGO_ACCESO_ID INTO v_codigo_acceso_id;

   IF v_codigo_acceso_id IS NULL OR NOT EXISTS (
      SELECT 1
      FROM CODIGO_ACCESO
      WHERE CODIGO_ACCESO_ID = v_codigo_acceso_id
        AND CODIGO_ACTIVO IS TRUE
        AND CODIGO_CREADO_EN IS NOT NULL
   ) THEN
      RAISE EXCEPTION 'FALLO: identificador o valores predeterminados de CODIGO_ACCESO';
   END IF;
   RAISE NOTICE 'OK: inserción y valores predeterminados de CODIGO_ACCESO';

   INSERT INTO INTENTO_ACCESO (CODIGO_ACCESO_ID, RESULTADO, MOTIVO)
   VALUES (v_codigo_acceso_id, 'AUTORIZADO', 'Prueba autorizada')
   RETURNING INTENTO_ACCESO_ID INTO v_intento_acceso_id;

   IF v_intento_acceso_id IS NULL OR NOT EXISTS (
      SELECT 1
      FROM INTENTO_ACCESO
      WHERE INTENTO_ACCESO_ID = v_intento_acceso_id
        AND FECHA_HORA IS NOT NULL
   ) THEN
      RAISE EXCEPTION 'FALLO: identificador o fecha predeterminada de INTENTO_ACCESO';
   END IF;
   RAISE NOTICE 'OK: inserción y valores predeterminados de INTENTO_ACCESO';

   /* Un intento con código desconocido puede almacenarse sin FK. */
   INSERT INTO INTENTO_ACCESO (CODIGO_ACCESO_ID, RESULTADO, MOTIVO)
   VALUES (NULL, 'RECHAZADO', 'Código no reconocido');
   RAISE NOTICE 'OK: intento rechazado sin código asociado';

   /* Una membresía histórica adicional sí está permitida. */
   INSERT INTO MEMBRESIA (
      PLAN_ID, MIEMBRO_ID, ESTADO, FECHA_INICIO, FECHA_FIN
   ) VALUES (
      v_plan_id, v_miembro_id, 'VENCIDA', DATE '2026-07-01', DATE '2026-07-31'
   );
   RAISE NOTICE 'OK: membresía histórica adicional';

   /*============================================================*/
   /* 2. Restricciones CHECK                                     */
   /*============================================================*/
   BEGIN
      INSERT INTO PLAN (NOMBRE, DURACION_DIAS)
      VALUES ('__PRUEBA_DURACION_INVALIDA__', 0);
      RAISE EXCEPTION 'FALLO: se permitió una duración menor que 1';
   EXCEPTION
      WHEN check_violation THEN
         RAISE NOTICE 'OK: duración menor que 1 rechazada';
   END;

   BEGIN
      INSERT INTO MEMBRESIA (
         PLAN_ID, MIEMBRO_ID, ESTADO, FECHA_INICIO, FECHA_FIN
      ) VALUES (
         v_plan_id, v_miembro_id, 'PENDIENTE', DATE '2026-10-01', DATE '2026-10-31'
      );
      RAISE EXCEPTION 'FALLO: se permitió un estado de membresía inválido';
   EXCEPTION
      WHEN check_violation THEN
         RAISE NOTICE 'OK: estado de membresía inválido rechazado';
   END;

   BEGIN
      INSERT INTO MEMBRESIA (
         PLAN_ID, MIEMBRO_ID, ESTADO, FECHA_INICIO, FECHA_FIN
      ) VALUES (
         v_plan_id, v_miembro_id, 'VENCIDA', DATE '2026-08-31', DATE '2026-08-01'
      );
      RAISE EXCEPTION 'FALLO: se permitió una fecha final anterior a la inicial';
   EXCEPTION
      WHEN check_violation THEN
         RAISE NOTICE 'OK: fecha final anterior a la inicial rechazada';
   END;

   BEGIN
      INSERT INTO CODIGO_ACCESO (MEMBRESIA_ID, CODIGO)
      VALUES (v_membresia_id, '__CODIGO CON ESPACIOS__');
      RAISE EXCEPTION 'FALLO: se permitió un código con espacios';
   EXCEPTION
      WHEN check_violation THEN
         RAISE NOTICE 'OK: código con espacios rechazado';
   END;

   BEGIN
      INSERT INTO INTENTO_ACCESO (CODIGO_ACCESO_ID, RESULTADO, MOTIVO)
      VALUES (v_codigo_acceso_id, 'PENDIENTE', 'Resultado inválido');
      RAISE EXCEPTION 'FALLO: se permitió un resultado inválido';
   EXCEPTION
      WHEN check_violation THEN
         RAISE NOTICE 'OK: resultado inválido rechazado';
   END;

   /*============================================================*/
   /* 3. Restricciones UNIQUE                                    */
   /*============================================================*/
   BEGIN
      INSERT INTO PLAN (NOMBRE, DURACION_DIAS)
      VALUES ('__PRUEBA_PLAN_30_DIAS__', 60);
      RAISE EXCEPTION 'FALLO: se permitió un nombre de plan duplicado';
   EXCEPTION
      WHEN unique_violation THEN
         RAISE NOTICE 'OK: nombre de plan duplicado rechazado';
   END;

   BEGIN
      INSERT INTO MIEMBRO (NOMBRES, APELLIDOS, NUMERO_DOCUMENTO)
      VALUES ('Otro', 'Miembro', '__PRUEBA_DOCUMENTO_001__');
      RAISE EXCEPTION 'FALLO: se permitió un documento duplicado';
   EXCEPTION
      WHEN unique_violation THEN
         RAISE NOTICE 'OK: número de documento duplicado rechazado';
   END;

   BEGIN
      INSERT INTO CODIGO_ACCESO (MEMBRESIA_ID, CODIGO)
      VALUES (v_membresia_id, '__PRUEBA_CODIGO_001__');
      RAISE EXCEPTION 'FALLO: se permitió un código duplicado';
   EXCEPTION
      WHEN unique_violation THEN
         RAISE NOTICE 'OK: código duplicado rechazado';
   END;

   BEGIN
      INSERT INTO MEMBRESIA (
         PLAN_ID, MIEMBRO_ID, ESTADO, FECHA_INICIO, FECHA_FIN
      ) VALUES (
         v_plan_id, v_miembro_id, 'ACTIVA', DATE '2026-10-01', DATE '2026-10-31'
      );
      RAISE EXCEPTION 'FALLO: se permitieron dos membresías activas para un miembro';
   EXCEPTION
      WHEN unique_violation THEN
         RAISE NOTICE 'OK: segunda membresía activa rechazada';
   END;

   /*============================================================*/
   /* 4. Claves foráneas y borrado restringido                   */
   /*============================================================*/
   BEGIN
      INSERT INTO MEMBRESIA (
         PLAN_ID, MIEMBRO_ID, ESTADO, FECHA_INICIO, FECHA_FIN
      ) VALUES (
         -1, v_miembro_id, 'VENCIDA', DATE '2026-06-01', DATE '2026-06-30'
      );
      RAISE EXCEPTION 'FALLO: se permitió una membresía con plan inexistente';
   EXCEPTION
      WHEN foreign_key_violation THEN
         RAISE NOTICE 'OK: plan inexistente rechazado';
   END;

   BEGIN
      INSERT INTO CODIGO_ACCESO (MEMBRESIA_ID, CODIGO)
      VALUES (-1, '__PRUEBA_CODIGO_FK__');
      RAISE EXCEPTION 'FALLO: se permitió un código con membresía inexistente';
   EXCEPTION
      WHEN foreign_key_violation THEN
         RAISE NOTICE 'OK: membresía inexistente rechazada en CODIGO_ACCESO';
   END;

   BEGIN
      INSERT INTO INTENTO_ACCESO (CODIGO_ACCESO_ID, RESULTADO, MOTIVO)
      VALUES (-1, 'RECHAZADO', 'Código inexistente');
      RAISE EXCEPTION 'FALLO: se permitió una FK de código inexistente';
   EXCEPTION
      WHEN foreign_key_violation THEN
         RAISE NOTICE 'OK: código inexistente rechazado en INTENTO_ACCESO';
   END;

   BEGIN
      DELETE FROM PLAN WHERE PLAN_ID = v_plan_id;
      RAISE EXCEPTION 'FALLO: se permitió borrar un plan con membresías';
   EXCEPTION
      WHEN foreign_key_violation THEN
         RAISE NOTICE 'OK: borrado de plan referenciado rechazado';
   END;

   RAISE NOTICE 'Todas las pruebas finalizaron correctamente';
END
$$ LANGUAGE plpgsql;

/* Elimina todos los datos ficticios y conserva intacto el esquema. */
ROLLBACK;
