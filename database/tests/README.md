# Pruebas de la base de datos

Esta carpeta contiene las pruebas del esquema PostgreSQL del Sistema de Gestión de Gimnasio.

## Requisitos

- PostgreSQL 16.
- Base de datos `sistema_gimnasio` creada.
- Esquema inicial ejecutado.

## Ejecución

1. Abrir la base de datos `sistema_gimnasio` en pgAdmin 4.
2. Abrir **Query Tool**.
3. Cargar `validar_esquema_restricciones.sql`.
4. Ejecutar el script completo.
5. Revisar la pestaña **Messages**.

## Cobertura

El script comprueba:

- Inserciones válidas en `PLAN`, `MIEMBRO`, `MEMBRESIA`, `CODIGO_ACCESO` e `INTENTO_ACCESO`.
- Generación automática de identificadores y fechas.
- Valores predeterminados de estados y campos booleanos.
- Restricciones `CHECK` de duración, fechas, estados, resultados y formato del código.
- Restricciones de unicidad para planes, documentos, códigos y membresías activas.
- Integridad de las claves foráneas.
- Conservación de membresías históricas.
- Registro de intentos rechazados sin código asociado.
- Restricción de borrado de registros referenciados.

## Resultado esperado

Cada validación debe mostrar un mensaje `NOTICE: OK`. Al finalizar debe aparecer:

```text
NOTICE: Todas las pruebas finalizaron correctamente
ROLLBACK
```

El `ROLLBACK` elimina los datos ficticios creados durante la ejecución y conserva intacto el esquema.

## Resultado de la validación

El script se ejecutó correctamente en PostgreSQL 16 el 8 de septiembre de 2026. Todas las validaciones finalizaron satisfactoriamente y la transacción terminó con `ROLLBACK`.
