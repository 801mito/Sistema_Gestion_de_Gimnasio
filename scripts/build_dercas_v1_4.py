"""Build DERCAS 1.4 using the established layout; preserve published PDFs.

Amendments are applied to the renderer's structured text/table inputs rather
than to an already rendered PDF. Historical generators retain their defaults.
"""
from __future__ import annotations

import sys
sys.dont_write_bytecode = True
import build_dercas_v1_3 as renderer

renderer.VERSION = "1.4"
renderer.ISSUE_DATE = "7 de noviembre de 2026"
renderer.SOURCE = renderer.DOCS / "DERCAS-Sistema-Gimnasio-MVP-v1.3.pdf"
renderer.OUTPUT = renderer.DOCS / "DERCAS-Sistema-Gimnasio-MVP-v1.4.pdf"
renderer.base.SOURCE = renderer.SOURCE

base = renderer.base
original_p = base.p
original_table = base.table

TEXT_CHANGES = {
    "RN-06: Todo intento se almacena, incluso cuando el código no existe.":
        "RN-06: Cada validación completada guarda exactamente un intento, incluso con código desconocido. Si falla la operación técnica, se revierte y se muestra ERROR, no una decisión de acceso.",
    "Las migraciones reproducibles V1_1, V1_2 y V1_3 incorporan, respectivamente, el modelo multitenant, las cuentas de empleados y la asociación de intentos con el gimnasio.":
        "Las migraciones V1_1, V1_2 y V1_3 incorporan multitenancy, empleados e intentos por gimnasio. V1_4 impide asociar un intento con un código de otro gimnasio y trasladar membresías o códigos entre gimnasios. No borra intentos históricos: la consulta oculta sus asociaciones ajenas.",
}

def paragraph(text, *args, **kwargs):
    return original_p(TEXT_CHANGES.get(text, text), *args, **kwargs)

def table(headers, rows, widths, font_size=8.2):
    amended = []
    for row in rows:
        row = list(row)
        if row[0] == "RF-06":
            row[2] = "Mostrar AUTORIZADO o RECHAZADO con motivo general solo después de guardar y confirmar el intento. Ante un fallo técnico, mostrar ERROR sin simular una decisión."
        elif row[0] == "RF-07":
            row[2] = "Guardar exactamente un intento por validación completada, con gimnasio, fecha/hora, resultado, motivo y código reconocido si existe. Un fallo técnico revierte la transacción; ERROR no es un resultado persistido."
        elif row[0] == "CA-07":
            row[-1] = "Cada validación completada deja exactamente un intento en su gimnasio, aun con código desconocido. Si falla el guardado, se revierte y se muestra ERROR sin anunciar AUTORIZADO ni RECHAZADO."
        elif headers == ["Paso", "Acción"] and row[0] == "4":
            row[1] = "Se guarda exactamente un intento en el gimnasio de la sesión y se confirma la transacción."
        elif headers == ["Paso", "Acción"] and row[0] == "5":
            row[1] = "Tras confirmar el guardado, se muestra AUTORIZADO o RECHAZADO. Ante un fallo técnico se revierte y se muestra ERROR; no se garantiza un intento persistido."
        amended.append(tuple(row))
    if amended and amended[-1][0] == "RF-12":
        amended.append(("RF-13", "Registrar plan",
            "Registrar nombre y duración positiva en el gimnasio de la sesión. Aceptación: el nombre es único dentro del gimnasio y el plan queda disponible para asignar membresías."))
    if amended and amended[-1][0] == "CA-12":
        amended.append(("CA-13",
            "El registro de un plan conserva el gimnasio autenticado, valida duración positiva y rechaza nombres duplicados allí; permite el mismo nombre en otro gimnasio."))
    if headers == ["Versión", "Fecha", "Descripción"]:
        amended.append(("1.4", renderer.ISSUE_DATE,
            "Alineación de modelos editables y exportaciones con el SQL vigente; registro de planes en el DFD; migración V1_4, orden transaccional y errores técnicos. Actualización del diccionario extendido."))
    return original_table(headers, amended, widths, font_size)

base.p = paragraph
base.table = table

if __name__ == "__main__":
    renderer.main()
