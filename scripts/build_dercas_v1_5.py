"""Build DERCAS 1.5: clarify administrative provisioning without new features.

Reuse the 1.4 content/layout and preserve all previously published PDFs.
"""
from __future__ import annotations

import sys

sys.dont_write_bytecode = True
import build_dercas_v1_4 as previous

renderer = previous.renderer
base = renderer.base
renderer.VERSION = "1.5"
renderer.ISSUE_DATE = "7 de noviembre de 2026"
renderer.SOURCE = renderer.DOCS / "DERCAS-Sistema-Gimnasio-MVP-v1.4.pdf"
renderer.OUTPUT = renderer.DOCS / "DERCAS-Sistema-Gimnasio-MVP-v1.5.pdf"
base.SOURCE = renderer.SOURCE

TEXT_CHANGES = {
    "Alta y autenticación inicial de empleados, asociando cada cuenta a un gimnasio activo.":
        "Aprovisionamiento local de la primera cuenta de cada gimnasio activo y autenticación de empleados.",
    "Tampoco se incluye un panel de administración de empleados ni una API REST.":
        "No se incluyen pantallas de alta o administración de gimnasios y empleados, autorregistro ni API REST.",
    "La primera cuenta de empleado se aprovisiona localmente; este MVP no incluye un panel para crear o administrar empleados.":
        "<b>Aprovisionamiento administrativo fuera de la aplicación.</b><br/>"
        "<b>Gimnasios:</b> el administrador técnico registra cada gimnasio mediante SQL en PostgreSQL "
        "(por ejemplo, desde pgAdmin). El MVP no ofrece una pantalla de alta de gimnasios ni autorregistro.<br/>"
        "<b>Primera cuenta:</b> una vez existente y activo el gimnasio, el administrador ejecuta "
        "EmployeeBootstrap en una terminal local interactiva, como PowerShell. La herramienta solicita "
        "el gimnasio, usuario, contraseña y confirmación; guarda el hash, no la contraseña en claro. "
        "Solo permite la primera cuenta de cada gimnasio y rechaza el alta si ese gimnasio ya tiene una cuenta.<br/>"
        "<b>Cuentas adicionales:</b> no existe un panel ni una herramienta administrativa incluida para "
        "agregar empleados adicionales al mismo gimnasio. Esa gestión queda fuera del alcance de este MVP; "
        "no debe confundirse con el aprovisionamiento de la primera cuenta. El administrador técnico "
        "requiere acceso controlado a la base; no se incorpora un rol o módulo de administración en la interfaz.",
}
seen_changes = set()

def paragraph(text, *args, **kwargs):
    for old, new in TEXT_CHANGES.items():
        if old in text:
            seen_changes.add(old)
            text = text.replace(old, new)
    return previous.paragraph(text, *args, **kwargs)

def table(headers, rows, widths, font_size=8.2):
    if headers == ["Versión", "Fecha", "Descripción"]:
        # The 1.3 renderer supplies versions 1.0-1.3. Add both later records
        # explicitly, keeping 1.4's original date and description intact.
        rows = list(rows) + [
            ("1.4", "7 de noviembre de 2026",
             "Alineación de modelos editables y exportaciones con el SQL vigente; registro de planes en el DFD; migración V1_4, orden transaccional y errores técnicos. Actualización del diccionario extendido."),
            ("1.5", renderer.ISSUE_DATE,
             "Aclaración del alta de gimnasios mediante SQL y de la primera cuenta con EmployeeBootstrap. Se explicita que no hay pantallas de administración ni herramienta para empleados adicionales; no se amplía el alcance funcional."),
        ]
        return previous.original_table(headers, rows, widths, font_size)
    return previous.table(headers, rows, widths, font_size)

base.p = paragraph
base.table = table

if __name__ == "__main__":
    renderer.main()
    missing = set(TEXT_CHANGES) - seen_changes
    if missing:
        raise RuntimeError(f"No se aplicaron todas las aclaraciones: {sorted(missing)}")
