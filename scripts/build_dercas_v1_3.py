"""Build DERCAS v1.3, incorporating the current DFD and ER diagrams.

The functional scope and requirements remain those of v1.2. This version
updates the four modeling views and leaves v1.2 unchanged as a historical copy.
"""

from __future__ import annotations

import importlib.util
import sys
from io import BytesIO
from pathlib import Path

from pypdf import PdfReader, PdfWriter
from reportlab.lib import colors
from reportlab.lib.pagesizes import letter, landscape
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas
from reportlab.platypus import Flowable, Spacer


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
SOURCE = DOCS / "DERCAS-Sistema-Gimnasio-MVP-v1.2.pdf"
OUTPUT = DOCS / "DERCAS-Sistema-Gimnasio-MVP-v1.3.pdf"
DIAGRAMS = DOCS / "diagramas"
PAGE_W, PAGE_H = letter
LEFT = 38
RIGHT = 38
CONTENT_W = PAGE_W - LEFT - RIGHT

BASE_SCRIPT = ROOT / "scripts" / "build_dercas_v1_2.py"
spec = importlib.util.spec_from_file_location("dercas_v12_builder", BASE_SCRIPT)
if spec is None or spec.loader is None:
    raise RuntimeError(f"No se pudo cargar el generador base: {BASE_SCRIPT}")
sys.dont_write_bytecode = True
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
base.SOURCE = SOURCE


def intro():
    s = [base.heading("1. Propósito del documento")]
    s.append(base.p(
        "Este DERCAS define los requisitos del MVP del Sistema de Gestión de Gimnasio. "
        "El producto registra miembros, asigna membresías y decide si un acceso es "
        "autorizado o rechazado, en una misma instalación utilizada por varios gimnasios "
        "con datos separados. No describe el sistema completo del gimnasio."
    ))
    s += [base.heading("1.1 Requerimiento del sistema", 2)]
    s.append(base.p(
        "Desarrollar una aplicación de escritorio JavaFX conectada a PostgreSQL para "
        "administrar miembros, planes y membresías, y controlar el acceso según el estado "
        "y vigencia de cada membresía. Una instalación puede atender a varios gimnasios "
        "sin mezclar sus registros."
    ))
    s.append(base.p(
        "Cada empleado inicia sesión con usuario y contraseña y pertenece a un solo "
        "gimnasio. La cuenta autenticada determina el gimnasio de trabajo para toda la "
        "sesión; no existe un selector manual de gimnasio. Los módulos de miembros, planes, "
        "membresías y accesos consultan y modifican únicamente los datos de ese contexto. "
        "Al cerrar sesión se descarta el contexto."
    ))
    s.append(base.p(
        "El MVP también genera un código único de acceso, valida el ingreso y registra "
        "cada intento. El empleado es una cuenta operativa; el miembro es la persona que "
        "posee una membresía y presenta un código. El aislamiento se aplica en los "
        "repositorios de la aplicación y mediante restricciones de integridad en PostgreSQL."
    ))
    s += [base.heading("2. Alcance del MVP"), base.heading("2.1 Incluido", 2)]
    for item in [
        "Alta y autenticación inicial de empleados, asociando cada cuenta a un gimnasio activo.",
        "Contexto de gimnasio de la sesión y separación de consultas y operaciones por gimnasio.",
        "Registro de miembros y planes; asignación de membresías con estados ACTIVA, CONGELADA y VENCIDA.",
        "Generación de un código de acceso único; ingreso manual, decisión y motivo de rechazo.",
        "Registro y consulta de todos los intentos de acceso, incluidos los de códigos inexistentes, por gimnasio.",
    ]:
        s.append(base.bullet(item))
    s += [base.heading("2.2 Fuera del alcance", 2)]
    s.append(base.p(
        "Punto de venta, inventario, facturación, pagos, reservas, control de aforo, rutinas, "
        "torniquetes, biometría, aplicación móvil, notificaciones y reportes avanzados. "
        "Tampoco se incluye un panel de administración de empleados ni una API REST."
    ))
    s += [base.heading("3. Actores")]
    s.append(base.table(["Actor", "Responsabilidad en el MVP"], [
        ("Administrador / Recepcionista", "Empleado autenticado: registra y consulta datos del gimnasio de su cuenta; valida accesos."),
        ("Miembro", "Presenta su código para solicitar el ingreso a su gimnasio."),
        ("Sistema", "Establece el contexto autenticado, valida código, estado, vigencia y gimnasio; registra el intento."),
    ], [151, CONTENT_W - 151]))
    s += [Spacer(1, 8), base.p(
        "<b>4. Modelado del sistema.</b> Esta versión reúne el Diagrama de Contexto y el "
        "DFD Nivel 0, ambos versión 1.1, y añade los modelos entidad-relación conceptual "
        "y físico PostgreSQL, también versión 1.1. Estas vistas documentan los procesos, "
        "entidades y relaciones del MVP multigimnasio.", base.SMALL
    )]
    return base.fragment(s)


def cover():
    def draw(c):
        c.setFont("Arial-Bold", 16)
        c.drawCentredString(PAGE_W / 2, 740, "DOCUMENTO DE ESPECIFICACIÓN DE REQUISITOS")
        c.drawCentredString(PAGE_W / 2, 708, "DERCAS del Sistema de Gestión de Gimnasio")
        c.setFont("Arial", 11)
        c.drawCentredString(PAGE_W / 2, 674, "Producto Mínimo Viable: membresías y accesos por gimnasio autenticado")
        info = [
            ("Proyecto", "Sistema de Gestión de Gimnasio"),
            ("Documento", "DERCAS del MVP"),
            ("Versión", "1.3"),
            ("Base de datos", "PostgreSQL"),
            ("Responsable", "Jaime Cardona"),
            ("Revisor", "Hendrick Calderon"),
            ("Fecha", "25 de octubre de 2026"),
        ]
        x, y, row_h = LEFT, 628, 23
        c.setStrokeColor(colors.HexColor("#696969"))
        for label, value in info:
            c.rect(x, y - row_h, CONTENT_W, row_h)
            c.line(x + CONTENT_W / 2, y, x + CONTENT_W / 2, y - row_h)
            c.setFont("Arial-Bold", 9.2)
            c.drawString(x + 6, y - 15, label)
            c.setFont("Arial", 9.0)
            c.drawString(x + CONTENT_W / 2 + 6, y - 15, value)
            y -= row_h
        c.setFillColor(colors.HexColor("#e7e7e7"))
        c.rect(x, 403, 150, 42, fill=1, stroke=1)
        c.setFillColor(colors.black)
        c.rect(x + 150, 403, CONTENT_W - 150, 42, fill=0, stroke=1)
        c.setFont("Arial-Bold", 10)
        c.drawCentredString(x + 75, 420, "Objetivo central")
        c.setFont("Arial", 9.5)
        c.drawCentredString(x + 150 + (CONTENT_W - 150) / 2, 428, "Decidir y registrar el acceso de un miembro")
        c.drawCentredString(x + 150 + (CONTENT_W - 150) / 2, 414, "sin mezclar datos de gimnasios distintos.")
    return base.single_page(draw)


def index(starts):
    def draw(c):
        c.setFont("Arial-Bold", 14)
        c.drawString(LEFT, 723, "Índice")
        items = [
            ("1. Propósito y requerimiento del sistema", "intro"),
            ("2. Alcance del MVP", "intro"),
            ("3. Actores", "intro"),
            ("4. Modelado del sistema", "diagram_context"),
            ("   4.1 Diagrama de Contexto", "diagram_context"),
            ("   4.2 Diagrama de Flujo de Datos - Nivel 0", "diagram_level0"),
            ("   4.3 Modelo Entidad-Relación conceptual", "der_conceptual"),
            ("   4.4 Modelo Entidad-Relación físico", "der_physical"),
            ("5. Requisitos funcionales", "functional"),
            ("6. Reglas de negocio", "rules"),
            ("   6.1 Tabla de decisión de acceso", "rules"),
            ("   6.2 Flujo principal", "rules"),
            ("7. Modelo de datos mínimo", "model"),
            ("   7.1 Integridad y aislamiento", "model"),
            ("   7.2 Diccionario de datos", "dictionary"),
            ("8. Requisitos no funcionales mínimos", "nonfunctional"),
            ("9. Criterios de aceptación del MVP", "acceptance"),
            ("10. Control de versiones y aprobación", "revisions"),
        ]
        y = 688
        for label, key in items:
            bold = not label.startswith("   ")
            c.setFont("Arial-Bold" if bold else "Arial", 9.7)
            c.drawString(LEFT + 7, y, label)
            c.drawRightString(PAGE_W - RIGHT - 7, y, str(starts[key]))
            y -= 28 if bold else 23
    return base.single_page(draw)


def diagram_page(title: str, path: Path, page_size):
    if not path.is_file():
        raise FileNotFoundError(f"No se encontró el diagrama requerido: {path}")
    page_w, page_h = page_size
    stream = BytesIO()
    c = canvas.Canvas(stream, pagesize=page_size)
    c.setTitle(title)
    c.setFillColor(colors.HexColor("#183153"))
    c.setFont("Arial-Bold", 13)
    c.drawString(36, page_h - 30, title)
    c.setFillColor(colors.HexColor("#555555"))
    c.setFont("Arial", 8.5)
    c.drawString(36, page_h - 46, "Diagrama exportado desde PowerDesigner - modelo versión 1.1")
    image = ImageReader(str(path))
    image_w, image_h = image.getSize()
    max_w = page_w - 72
    max_h = page_h - 122
    scale = min(max_w / image_w, max_h / image_h)
    draw_w, draw_h = image_w * scale, image_h * scale
    x = (page_w - draw_w) / 2
    y = 62 + (max_h - draw_h) / 2
    c.drawImage(image, x, y, width=draw_w, height=draw_h, mask="auto")
    c.showPage()
    c.save()
    stream.seek(0)
    return PdfReader(stream).pages[0]


def revisions():
    s = [base.heading("10. Control de versiones y aprobación")]
    s.append(base.table(["Versión", "Fecha", "Descripción"], [
        ("1.0", "08 de agosto de 2026", "Emisión inicial del DERCAS limitado al MVP."),
        ("1.1", "26 de agosto de 2026", "Reformulación del requerimiento general, índice, diagramas, diccionario de datos, criterios de aceptación y sección de aprobación."),
        ("1.2", "17 de octubre de 2026", "Integración multitenant en el requerimiento, alcance, actores, requisitos, reglas, modelo, diccionario, seguridad y criterios de aceptación."),
        ("1.3", "25 de octubre de 2026", "Incorporación de los DFD y modelos DER versión 1.1; se conserva el alcance funcional multigimnasio de la versión 1.2."),
    ], [52, 131, CONTENT_W - 183], 7.8))
    s += [Spacer(1, 22), base.p("Aprobación correspondiente a la versión 1.3:", base.SMALL), Spacer(1, 26), ApprovalBlock()]
    return base.fragment(s)


class ApprovalBlock(Flowable):
    """Blank approval lines so v1.3 is not presented as newly signed."""

    def __init__(self):
        super().__init__()
        self.width = CONTENT_W
        self.height = 88

    def draw(self):
        c = self.canv
        c.setStrokeColor(colors.black)
        c.line(10, 53, 231, 53)
        c.line(305, 53, 526, 53)
        c.setFont("Arial-Bold", 9.4)
        c.drawCentredString(120, 33, "JAIME CARDONA")
        c.drawCentredString(415, 33, "HENDRICK CALDERON")
        c.setFont("Arial", 9.1)
        c.drawCentredString(120, 18, "Responsable del proyecto")
        c.drawCentredString(415, 18, "Ingeniero revisor")


def footer(page, page_number, diagram_numbers):
    page_w = float(page.mediabox.width)
    page_h = float(page.mediabox.height)
    stream = BytesIO()
    c = canvas.Canvas(stream, pagesize=(page_w, page_h))
    c.setFillColor(colors.white)
    c.rect(0, 0, page_w, 52, fill=1, stroke=0)
    c.setStrokeColor(colors.HexColor("#b6b6b6"))
    c.line(LEFT, 52, page_w - RIGHT, 52)
    c.setFillColor(colors.black)
    if page_number != 1 and page_number not in diagram_numbers:
        c.setFont("Arial-Bold", 9)
        c.drawString(LEFT, page_h - 28, "DERCAS - Sistema de Gestión de Gimnasio")
    c.setFont("Arial", 9)
    c.drawRightString(page_w - RIGHT, 25, f"Versión 1.3 | Página {page_number}")
    c.save()
    stream.seek(0)
    page.merge_page(PdfReader(stream).pages[0])


def main():
    if not SOURCE.is_file():
        raise FileNotFoundError(f"No se encontró la versión de origen: {SOURCE}")
    if OUTPUT.exists():
        raise FileExistsError(f"No se sobrescribirá un DERCAS existente: {OUTPUT}")

    parts = {
        "intro": intro(),
        "functional": base.functional(),
        "rules": base.rules(),
        "model": base.model(),
        "dictionary": base.dictionary(),
        "nonfunctional": base.nonfunctional(),
        "acceptance": base.acceptance(),
        "revisions": revisions(),
    }
    starts = {"intro": 3}
    number = 3 + len(parts["intro"].pages)
    starts["diagram_context"] = number
    starts["diagram_level0"] = number + 1
    starts["der_conceptual"] = number + 2
    starts["der_physical"] = number + 3
    diagram_numbers = set(range(number, number + 4))
    number += 4
    for key in ["functional", "rules", "model", "dictionary", "nonfunctional", "acceptance", "revisions"]:
        starts[key] = number
        number += len(parts[key].pages)

    diagram_pages = [
        diagram_page("4.1 Diagrama de Contexto", DIAGRAMS / "dfd" / "DFD_Contexto_1.1.png", letter),
        diagram_page("4.2 Diagrama de Flujo de Datos - Nivel 0", DIAGRAMS / "dfd" / "DFD_Nivel_0_1.1.png", letter),
        diagram_page("4.3 Modelo Entidad-Relación conceptual", DIAGRAMS / "der" / "DER_Sistema_Gimnasio_Conceptual_v1.1.png", landscape(letter)),
        diagram_page("4.4 Modelo Entidad-Relación físico PostgreSQL", DIAGRAMS / "der" / "DER_Sistema_Gimnasio_Fisico_v1.1.png", landscape(letter)),
    ]

    writer = PdfWriter()
    writer.add_page(cover().pages[0])
    writer.add_page(index(starts).pages[0])
    for page in parts["intro"].pages:
        writer.add_page(page)
    for page in diagram_pages:
        writer.add_page(page)
    for key in ["functional", "rules", "model", "dictionary", "nonfunctional", "acceptance", "revisions"]:
        for page in parts[key].pages:
            writer.add_page(page)
    for i, page in enumerate(writer.pages, 1):
        footer(page, i, diagram_numbers)
    writer.add_metadata({
        "/Title": "DERCAS - Sistema de Gestión de Gimnasio - MVP v1.3",
        "/Author": "Jaime Cardona",
        "/Subject": "Requisitos del MVP y diagramas actualizados del sistema",
    })
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("wb") as out:
        writer.write(out)
    print(f"Wrote {OUTPUT} ({len(writer.pages)} pages)")
    print("Section starts:", starts)


if __name__ == "__main__":
    main()

