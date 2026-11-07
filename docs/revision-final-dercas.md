# Revisión final de modelos y documentación

Fecha: 7 de noviembre de 2026. Seguimiento: issue #85.

## Cambios documentales

- CDM/PDM: nombres físicos, tipos, valores iniciales, obligatoriedad,
  unicidades por gimnasio, claves compuestas y referencias de membresía.
- PDM: claves primarias con nombres distintos y un índice único parcial para
  una membresía ACTIVA por miembro; no restringe todo el historial.
- DFD: proceso 7 Registrar plan, entrada del empleado, escritura en Planes y
  confirmación. El contexto agrupa estos datos y los de asignación de membresía.
- Diccionario: flujos F21-F23, estructuras, elementos y almacén Planes;
  referencias a V1_4 y separación entre decisión de acceso y error técnico.
- DERCAS 1.4: imágenes actualizadas, referencia a V1_4 y guardado confirmado
  antes de mostrar la decisión. La versión 1.3 queda como antecedente.
  RF-13 y CA-13 hacen explícito el registro de planes que ya existe en la
  aplicación y en el alcance; no añaden una funcionalidad nueva.

## Comprobaciones realizadas

| Comprobación | Resultado y alcance |
| --- | --- |
| `mvn -B clean verify -Ppostgres-it` | 146 pruebas aprobadas: 27 unitarias y 119 de integración, sin errores, fallos ni omisiones. Ejecución del repaso previo a estas correcciones documentales. |
| SQL nativo del PDM en PostgreSQL 16 temporal | Ejecutado correctamente; cotejo de columnas, tipos, nulabilidad, valores iniciales, PK, UNIQUE y FK contra el SQL de referencia. |
| CHECK e índice único parcial | Cotejados por separado; la condición de membresía ACTIVA se conserva. No equivale a comparar todos los índices no únicos de rendimiento. |
| Migración V1_4 | Aplicada por separado a los esquemas temporales comparados. Los disparadores permanecen en la migración, no se representan como simples relaciones del DER. |
| PowerDesigner DFD | Comprobación nativa: 0 errores y 0 advertencias. |
| PDF | Revisión de páginas renderizadas, tablas, numeración e imágenes; formato gris/negro. |

Los modelos son documentación: no reemplazan el procedimiento de migraciones.
No se ejecutó el SQL generado sobre la base habitual ni se cambiaron datos o
funcionalidades de la aplicación durante esta corrección.

## Límites de la verificación

Las pruebas automatizadas y el cotejo de esquema no sustituyen una sesión
manual completa en la aplicación con dos gimnasios. El sondeo de tiempos del
manejador JavaFX tampoco constituye una medición visual de extremo a extremo.
La [lista de pruebas manuales](pruebas-manuales-acceso.md) conserva sus
resultados pendientes hasta que se ejecuten en un entorno de prueba.

El PDM conserva advertencias de modelado por claves compuestas, inclusiones
de índices y documento opcional; no se alteró la regla de negocio para
silenciarlas. Su SQL se validó adicionalmente en PostgreSQL.
