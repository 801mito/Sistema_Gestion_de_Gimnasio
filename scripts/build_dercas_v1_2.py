"""Build DERCAS 1.2 with multitenant requirements in the main sections.

The two approved modeling diagrams are imported unchanged from DERCAS 1.1.
The signed 1.1 PDF remains the historical approval record. At the author's
request, his handwritten signature is carried into the updated version;
the reviewer's signature is left blank.
"""

from __future__ import annotations

from io import BytesIO
from pathlib import Path
from xml.sax.saxutils import escape

from pypdf import PdfReader, PdfWriter
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.lib.utils import ImageReader
from reportlab.platypus import (
    Flowable,
    KeepTogether,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs" / "DERCAS-Sistema-Gimnasio-MVP-v1.1.pdf"
OUTPUT = ROOT / "docs" / "DERCAS-Sistema-Gimnasio-MVP-v1.2.pdf"
PAGE_W, PAGE_H = letter
LEFT = 38
RIGHT = 38
CONTENT_W = PAGE_W - LEFT - RIGHT

pdfmetrics.registerFont(TTFont("Arial", "C:/Windows/Fonts/arial.ttf"))
pdfmetrics.registerFont(TTFont("Arial-Bold", "C:/Windows/Fonts/arialbd.ttf"))
pdfmetrics.registerFontFamily("Arial", normal="Arial", bold="Arial-Bold")

BODY = ParagraphStyle("body", fontName="Arial", fontSize=9.3, leading=12.6, spaceAfter=6)
SMALL = ParagraphStyle("small", parent=BODY, fontSize=8.2, leading=10.8, spaceAfter=3)
CELL = ParagraphStyle("cell", parent=BODY, fontSize=8.2, leading=10.4, spaceAfter=0)
CELL_HEAD = ParagraphStyle("head", parent=CELL, fontName="Arial-Bold")
H1 = ParagraphStyle("h1", parent=BODY, fontName="Arial-Bold", fontSize=13.5, leading=17, spaceBefore=7, spaceAfter=9)
H2 = ParagraphStyle("h2", parent=BODY, fontName="Arial-Bold", fontSize=10.3, leading=13, spaceBefore=8, spaceAfter=5)
SUB = ParagraphStyle("sub", parent=BODY, fontName="Arial-Bold", fontSize=9.3, leading=12, spaceBefore=6, spaceAfter=3)


def p(text: str, style=BODY) -> Paragraph:
    return Paragraph(text, style)


def heading(text: str, level: int = 1) -> Paragraph:
    return p(text, H1 if level == 1 else H2)


def bullet(text: str) -> Paragraph:
    return p("&#8226; " + text, BODY)


def table(headers, rows, widths, font_size=8.2):
    style = ParagraphStyle("row", parent=CELL, fontSize=font_size, leading=font_size + 2.2)
    head = [p(escape(str(x)), CELL_HEAD) for x in headers]
    data = [head] + [[p(escape(str(x)), style) for x in row] for row in rows]
    t = Table(data, colWidths=widths, repeatRows=1, hAlign="LEFT")
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#e7e7e7")),
        ("GRID", (0, 0), (-1, -1), 0.45, colors.HexColor("#6b6b6b")),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 5),
        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    return t


def fragment(story) -> PdfReader:
    stream = BytesIO()
    doc = SimpleDocTemplate(
        stream, pagesize=letter, leftMargin=LEFT, rightMargin=RIGHT,
        topMargin=53, bottomMargin=58, title="DERCAS 1.2",
    )
    doc.build(story)
    stream.seek(0)
    return PdfReader(stream)


def intro():
    s = [heading("1. Propósito del documento")]
    s += [p("Este DERCAS define los requisitos del MVP del Sistema de Gestión de Gimnasio. El producto registra miembros, asigna membresías y decide si un acceso es autorizado o rechazado, ahora en una misma instalación utilizada por varios gimnasios con datos separados. No describe el sistema completo del gimnasio.")]
    s += [heading("1.1 Requerimiento del sistema", 2)]
    s += [p("Desarrollar una aplicación de escritorio JavaFX conectada a PostgreSQL para administrar miembros, planes y membresías, y controlar el acceso según el estado y vigencia de cada membresía. Una instalación puede atender a varios gimnasios sin mezclar sus registros.")]
    s += [p("Cada empleado inicia sesión con usuario y contraseña y pertenece a un solo gimnasio. La cuenta autenticada determina el gimnasio de trabajo para toda la sesión; no existe un selector manual de gimnasio. Los módulos de miembros, planes, membresías y accesos consultan y modifican únicamente los datos de ese contexto. Al cerrar sesión se descarta el contexto.")]
    s += [p("El MVP también genera un código único de acceso, valida el ingreso y registra cada intento. El empleado es una cuenta operativa; el miembro es la persona que posee una membresía y presenta un código. El aislamiento se aplica en los repositorios de la aplicación y mediante restricciones de integridad en PostgreSQL.")]
    s += [heading("2. Alcance del MVP")]
    s += [heading("2.1 Incluido", 2)]
    for x in [
        "Alta y autenticación inicial de empleados, asociando cada cuenta a un gimnasio activo.",
        "Contexto de gimnasio de la sesión y separación de consultas y operaciones por gimnasio.",
        "Registro de miembros y planes; asignación de membresías con estados ACTIVA, CONGELADA y VENCIDA.",
        "Generación de un código de acceso único; ingreso manual, decisión y motivo de rechazo.",
        "Registro y consulta de todos los intentos de acceso, incluidos los de códigos inexistentes, por gimnasio.",
    ]:
        s.append(bullet(x))
    s += [heading("2.2 Fuera del alcance", 2)]
    s += [p("Punto de venta, inventario, facturación, pagos, reservas, control de aforo, rutinas, torniquetes, biometría, aplicación móvil, notificaciones y reportes avanzados. Tampoco se incluye un panel de administración de empleados ni una API REST.")]
    s += [heading("3. Actores")]
    s += [table(["Actor", "Responsabilidad en el MVP"], [
        ("Administrador / Recepcionista", "Empleado autenticado: registra y consulta datos del gimnasio de su cuenta; valida accesos."),
        ("Miembro", "Presenta su código para solicitar el ingreso a su gimnasio."),
        ("Sistema", "Establece el contexto autenticado, valida código, estado, vigencia y gimnasio; registra el intento."),
    ], [151, CONTENT_W - 151])]
    s += [Spacer(1, 8), p("<b>4. Modelado del sistema.</b> Se conservan sin cambios el Diagrama de Contexto y el DFD Nivel 0 de la versión 1.1. La identificación del gimnasio desde la sesión y el aislamiento de datos son requisitos transversales descritos en las secciones siguientes.", SMALL)]
    return fragment(s)


def functional():
    rows = [
        ("RF-01", "Registrar miembro", "Registrar un miembro con nombres y apellidos en el gimnasio de la sesión; documento, teléfono y correo son opcionales. Aceptación: queda almacenado y es consultable solo allí."),
        ("RF-02", "Asignar plan y membresía", "Seleccionar un plan del mismo gimnasio y crear una membresía con fechas válidas y estado inicial ACTIVA. Aceptación: queda vinculada al miembro, plan y gimnasio correspondientes."),
        ("RF-03", "Generar código", "Generar un código alfanumérico único y no predecible asociado a la membresía. Aceptación: no se comparte entre miembros y se puede ingresar manualmente."),
        ("RF-04", "Manejar estados", "Permitir ACTIVA, CONGELADA y VENCIDA. Aceptación: solo ACTIVA dentro de sus fechas puede autorizar el ingreso."),
        ("RF-05", "Ingresar código", "Permitir el ingreso manual y buscar la membresía asociada dentro del gimnasio autenticado. Aceptación: procesa códigos válidos, desconocidos o inactivos sin detener la aplicación."),
        ("RF-06", "Mostrar decisión", "Mostrar AUTORIZADO o RECHAZADO con motivo general. Aceptación: coincide con código, gimnasio, estado y vigencia."),
        ("RF-07", "Guardar intento", "Guardar cada intento con gimnasio, fecha y hora, resultado, motivo y código reconocido cuando exista. Aceptación: cada validación produce exactamente un registro, aun con código desconocido."),
        ("RF-08", "Autenticar empleado", "Verificar usuario y contraseña contra un hash; permitir solo cuenta y gimnasio activos. Aceptación: credenciales incorrectas o inactivas no abren sesión."),
        ("RF-09", "Crear contexto de sesión", "Cargar empleado y gimnasio autenticados, compartirlos entre módulos y descartarlos al cerrar sesión. Aceptación: otro gimnasio requiere nueva autenticación."),
        ("RF-10", "Aislar operaciones", "Filtrar lecturas, altas, ediciones y cambios de estado por el gimnasio de la sesión. Aceptación: un empleado no consulta ni modifica datos de otro gimnasio desde la aplicación."),
        ("RF-11", "Validar relaciones", "Impedir que una membresía relacione miembro y plan de gimnasios distintos. Aceptación: PostgreSQL rechaza esa combinación mediante llaves foráneas compuestas."),
        ("RF-12", "Aislar historial", "Registrar y mostrar los intentos del gimnasio de la sesión, incluso cuando el código no existe. Aceptación: los historiales de dos gimnasios no se mezclan."),
    ]
    return fragment([heading("5. Requisitos funcionales"), table(["ID", "Requisito", "Descripción y criterio de aceptación"], rows, [43, 126, CONTENT_W - 169], 8.0)])


def rules():
    s = [heading("6. Reglas de negocio")]
    rules = [
        "RN-01: Se autoriza solo si el código existe, está activo y pertenece al gimnasio de la sesión, y la membresía está ACTIVA y vigente.",
        "RN-02: Las membresías CONGELADA o VENCIDA siempre producen RECHAZADO.",
        "RN-03: Una membresía ACTIVA fuera de sus fechas también produce RECHAZADO.",
        "RN-04: Cada miembro puede tener como máximo una membresía activa a la vez; las anteriores permanecen en el historial.",
        "RN-05: La fecha final no puede ser anterior a la fecha inicial.",
        "RN-06: Todo intento se almacena, incluso cuando el código no existe.",
        "RN-07: El código no se construye a partir de datos personales predecibles.",
        "RN-08: Cada empleado pertenece a un único gimnasio; el usuario es globalmente único.",
        "RN-09: El gimnasio de trabajo proviene de la cuenta autenticada y no cambia durante la sesión.",
        "RN-10: Las operaciones de miembros, planes, membresías y accesos se limitan al gimnasio de la sesión.",
        "RN-11: Una membresía solo relaciona un miembro y un plan del mismo gimnasio; la base valida las llaves compuestas.",
        "RN-12: El código obtiene su gimnasio mediante la membresía; no necesita gimnasio_id directo. El código es globalmente único.",
        "RN-13: Cada intento guarda gimnasio_id del contexto, aun con código inexistente; codigo_acceso_id puede quedar vacío.",
        "RN-14: Los nombres de plan y documentos de miembro son únicos dentro de cada gimnasio y pueden repetirse entre gimnasios.",
    ]
    s += [p(x, SMALL) for x in rules]
    s += [heading("6.1 Tabla de decisión de acceso", 2)]
    s += [table(["Código", "Membresía", "Fechas", "Resultado", "Motivo"], [
        ("Propio y activo", "ACTIVA", "Vigentes", "AUTORIZADO", "Membresía válida"),
        ("Propio y activo", "CONGELADA", "Cualquiera", "RECHAZADO", "Membresía congelada"),
        ("Propio y activo", "VENCIDA", "Cualquiera", "RECHAZADO", "Membresía vencida"),
        ("Propio y activo", "ACTIVA", "Fuera de vigencia", "RECHAZADO", "Fecha no vigente"),
        ("Inactivo", "Cualquiera", "Cualquiera", "RECHAZADO", "Código inactivo"),
        ("Inexistente u otro gimnasio", "No aplica", "No aplica", "RECHAZADO", "Código no reconocido"),
    ], [106, 89, 92, 84, CONTENT_W - 371], 7.3)]
    s += [heading("6.2 Flujo principal", 2)]
    s += [table(["Paso", "Acción"], [
        ("1", "El empleado inicia sesión y el sistema fija su gimnasio."),
        ("2", "El miembro presenta el código; el empleado lo ingresa manualmente."),
        ("3", "El sistema busca el código dentro del gimnasio autenticado y valida estado y fechas."),
        ("4", "Se muestra AUTORIZADO o RECHAZADO y un motivo general."),
        ("5", "Se registra el intento en el gimnasio de la sesión, con o sin código reconocido."),
    ], [44, CONTENT_W - 44])]
    return fragment(s)


def model():
    s = [heading("7. Modelo de datos mínimo")]
    s += [p("GIMNASIO es la raíz del tenant. Los registros operativos se vinculan a un gimnasio mediante gimnasio_id obligatorio o, en CODIGO_ACCESO, por su membresía. La base usa llaves foráneas, restricciones e índices para preservar las relaciones y las unicidades locales.")]
    s += [table(["Entidad", "Datos y relación con el gimnasio"], [
        ("GIMNASIO", "gimnasio_id, nombre único, gimnasio_activo, gimnasio_creado_en."),
        ("EMPLEADO", "empleado_id, gimnasio_id, usuario globalmente único, contrasena_hash, empleado_activo, empleado_creado_en."),
        ("PLAN", "plan_id, gimnasio_id, nombre único por gimnasio, duracion_dias, plan_activo, plan_creado_en."),
        ("MIEMBRO", "miembro_id, gimnasio_id, nombres, apellidos, numero_documento único por gimnasio si existe, teléfono, correo, miembro_creado_en."),
        ("MEMBRESIA", "membresia_id, gimnasio_id, miembro_id, plan_id, estado, fechas y membresia_creada_en. Llaves compuestas comprueban el mismo gimnasio."),
        ("CODIGO_ACCESO", "codigo_acceso_id, membresia_id, codigo único, codigo_activo, codigo_creado_en. Hereda el gimnasio a través de MEMBRESIA."),
        ("INTENTO_ACCESO", "intento_acceso_id, gimnasio_id, codigo_acceso_id opcional, resultado, motivo y fecha_hora. Registra también códigos desconocidos."),
    ], [118, CONTENT_W - 118])]
    s += [heading("7.1 Integridad y aislamiento", 2)]
    s += [p("MEMBRESIA referencia PLAN y MIEMBRO mediante (gimnasio_id, plan_id) y (gimnasio_id, miembro_id). Los índices por gimnasio respaldan las búsquedas y la unicidad local de planes y documentos. CODIGO_ACCESO no almacena gimnasio_id directamente. INTENTO_ACCESO sí lo almacena porque puede no existir código asociado.")]
    s += [p("Las migraciones reproducibles V1_1, V1_2 y V1_3 incorporan, respectivamente, el modelo multitenant, las cuentas de empleados y la asociación de intentos con el gimnasio.")]
    return fragment(s)


def dictionary():
    s = [heading("7.2 Diccionario de datos")]
    s += [p("Los nombres y tipos siguen el modelo físico PostgreSQL. S significa obligatorio; N significa opcional. Los campos de creación tienen valor inicial CURRENT_TIMESTAMP.", SMALL)]
    entities = [
        ("GIMNASIO", [
            ("GIMNASIO_ID", "SERIAL", "S", "Llave primaria."),
            ("NOMBRE", "VARCHAR(150)", "S", "Único globalmente."),
            ("GIMNASIO_ACTIVO", "BOOL", "S", "Estado; inicial TRUE."),
            ("GIMNASIO_CREADO_EN", "TIMESTAMP", "S", "Fecha de creación."),
        ]),
        ("EMPLEADO", [
            ("EMPLEADO_ID", "SERIAL", "S", "Llave primaria."),
            ("GIMNASIO_ID", "INT4", "S", "FK a GIMNASIO; un solo gimnasio."),
            ("USUARIO", "VARCHAR(100)", "S", "Único globalmente; minúsculas y formato validado."),
            ("CONTRASENA_HASH", "VARCHAR(255)", "S", "Hash; nunca contraseña en claro."),
            ("EMPLEADO_ACTIVO", "BOOL", "S", "Estado; inicial TRUE."),
            ("EMPLEADO_CREADO_EN", "TIMESTAMP", "S", "Fecha de creación."),
        ]),
        ("PLAN", [
            ("PLAN_ID", "SERIAL", "S", "Llave primaria."),
            ("GIMNASIO_ID", "INT4", "S", "FK a GIMNASIO."),
            ("NOMBRE", "VARCHAR(100)", "S", "Único por (GIMNASIO_ID, NOMBRE)."),
            ("DURACION_DIAS", "INT4", "S", "Al menos 1 día."),
            ("PLAN_ACTIVO", "BOOL", "S", "Estado; inicial TRUE."),
            ("PLAN_CREADO_EN", "TIMESTAMP", "S", "Fecha de creación."),
        ]),
        ("MIEMBRO", [
            ("MIEMBRO_ID", "SERIAL", "S", "Llave primaria."),
            ("GIMNASIO_ID", "INT4", "S", "FK a GIMNASIO."),
            ("NOMBRES", "VARCHAR(100)", "S", "Nombres del miembro."),
            ("APELLIDOS", "VARCHAR(100)", "S", "Apellidos del miembro."),
            ("NUMERO_DOCUMENTO", "VARCHAR(30)", "N", "Único por gimnasio si se proporciona."),
            ("TELEFONO", "VARCHAR(25)", "N", "Contacto."),
            ("CORREO", "VARCHAR(150)", "N", "Contacto."),
            ("MIEMBRO_CREADO_EN", "TIMESTAMP", "S", "Fecha de creación."),
        ]),
        ("MEMBRESIA", [
            ("MEMBRESIA_ID", "SERIAL", "S", "Llave primaria."),
            ("GIMNASIO_ID", "INT4", "S", "FK a GIMNASIO y parte de FKs compuestas."),
            ("MIEMBRO_ID", "INT4", "S", "FK compuesta con gimnasio_id a MIEMBRO."),
            ("PLAN_ID", "INT4", "S", "FK compuesta con gimnasio_id a PLAN."),
            ("ESTADO", "VARCHAR(20)", "S", "ACTIVA, CONGELADA o VENCIDA."),
            ("FECHA_INICIO", "DATE", "S", "Inicio de vigencia."),
            ("FECHA_FIN", "DATE", "S", "Igual o posterior a FECHA_INICIO."),
            ("MEMBRESIA_CREADA_EN", "TIMESTAMP", "S", "Fecha de creación."),
        ]),
        ("CODIGO_ACCESO", [
            ("CODIGO_ACCESO_ID", "SERIAL", "S", "Llave primaria."),
            ("MEMBRESIA_ID", "INT4", "S", "FK a MEMBRESIA; de ella deriva el gimnasio."),
            ("CODIGO", "VARCHAR(64)", "S", "Único globalmente, sin espacios."),
            ("CODIGO_ACTIVO", "BOOL", "S", "Estado; inicial TRUE."),
            ("CODIGO_CREADO_EN", "TIMESTAMP", "S", "Fecha de creación."),
        ]),
        ("INTENTO_ACCESO", [
            ("INTENTO_ACCESO_ID", "SERIAL", "S", "Llave primaria."),
            ("GIMNASIO_ID", "INT4", "S", "FK al gimnasio de la sesión."),
            ("CODIGO_ACCESO_ID", "INT4", "N", "FK opcional; vacío si no existe el código."),
            ("RESULTADO", "VARCHAR(12)", "S", "AUTORIZADO o RECHAZADO."),
            ("MOTIVO", "VARCHAR(100)", "S", "Motivo general."),
            ("FECHA_HORA", "TIMESTAMP", "S", "Momento del intento."),
        ]),
    ]
    for name, rows in entities:
        s += [p(name, SUB), table(["Campo", "Tipo", "Obl.", "Descripción / restricción"], rows, [137, 88, 30, CONTENT_W - 255], 7.75), Spacer(1, 8)]
    return fragment(s)


def nonfunctional():
    s = [heading("8. Requisitos no funcionales mínimos")]
    s += [table(["ID", "Categoría", "Requisito"], [
        ("RNF-01", "Persistencia", "La información se almacena en PostgreSQL mediante migraciones reproducibles."),
        ("RNF-02", "Integridad", "Las operaciones compuestas se realizan de forma atómica y las relaciones entre gimnasios se validan en la base."),
        ("RNF-03", "Rendimiento", "La decisión de acceso debe mostrarse en un máximo de 2 segundos en condiciones normales."),
        ("RNF-04", "Seguridad", "Secretos fuera del repositorio; códigos únicos no predecibles y contraseñas como hashes con sal aleatoria."),
        ("RNF-05", "Trazabilidad", "Los intentos no se eliminan como parte de la operación normal; cada uno queda asociado a un gimnasio."),
        ("RNF-06", "Usabilidad", "La interfaz distingue AUTORIZADO y RECHAZADO con motivo general y muestra el gimnasio autenticado."),
        ("RNF-07", "Aislamiento", "Todos los repositorios filtran por el gimnasio de la sesión y no reciben un gimnasio elegido desde la interfaz."),
    ], [57, 107, CONTENT_W - 164])]
    s += [heading("8.1 Justificación técnica y límites de seguridad", 2)]
    s += [p("PostgreSQL permite transacciones ACID, llaves foráneas compuestas, restricciones e índices que preservan las relaciones entre gimnasio, miembro, plan y membresía. Las credenciales de empleados se verifican mediante PBKDF2-HMAC-SHA-256 con sal aleatoria; la contraseña original no se guarda.")]
    s += [p("La aplicación JavaFX se conecta directamente a PostgreSQL mediante JDBC. El aislamiento entre gimnasios se aplica en el contexto autenticado y en los filtros de sus repositorios; el MVP no incluye API REST ni políticas RLS. Una persona con credenciales amplias de la base podría consultar datos directamente fuera de la aplicación. Antes de operar con datos reales deben definirse permisos de base de datos apropiados.")]
    s += [p("La primera cuenta de empleado se aprovisiona localmente; este MVP no incluye un panel para crear o administrar empleados.")]
    return fragment(s)


def acceptance():
    s = [heading("9. Criterios de aceptación del MVP")]
    s += [table(["ID", "Criterio verificable"], [
        ("CA-01", "Al registrar un miembro, sus datos quedan en PostgreSQL con identificador y gimnasio correctos."),
        ("CA-02", "Se asigna un plan del mismo gimnasio y una membresía con fechas válidas y estado inicial ACTIVA."),
        ("CA-03", "Los estados ACTIVA, CONGELADA y VENCIDA aplican sus reglas y conservan el historial."),
        ("CA-04", "Se genera un código único, no predecible y asociado a la membresía; puede ingresarse manualmente."),
        ("CA-05", "Un código propio y activo, con membresía ACTIVA y vigente, produce AUTORIZADO."),
        ("CA-06", "Código desconocido, inactivo o ajeno, o membresía congelada, vencida o fuera de fecha, produce RECHAZADO con motivo general."),
        ("CA-07", "Cada validación produce un intento con resultado, motivo, fecha/hora y gimnasio, incluso con código inexistente."),
        ("CA-08", "Los registros conservan las relaciones y restricciones de integridad definidas en PostgreSQL."),
        ("CA-09", "Dos empleados de gimnasios distintos ven su propio gimnasio y no consultan ni modifican datos del otro desde la aplicación."),
        ("CA-10", "Credenciales incorrectas, cuentas inactivas y gimnasios inactivos no permiten iniciar sesión."),
        ("CA-11", "PostgreSQL rechaza una membresía que combine miembro y plan de gimnasios distintos."),
        ("CA-12", "Los intentos, incluso con códigos inexistentes, aparecen solo en el historial del gimnasio donde se realizaron."),
    ], [57, CONTENT_W - 57])]
    s += [Spacer(1, 7), p("Cualquier funcionalidad no descrita expresamente se considera fuera del MVP y debe planificarse en iteraciones posteriores.", SMALL)]
    return fragment(s)


def revisions():
    s = [heading("10. Control de versiones y aprobación")]
    s += [table(["Versión", "Fecha", "Descripción"], [
        ("1.0", "08 de agosto de 2026", "Emisión inicial del DERCAS limitado al MVP."),
        ("1.1", "26 de agosto de 2026", "Reformulación del requerimiento general, índice, diagramas, diccionario de datos, criterios de aceptación y sección de aprobación."),
        ("1.2", "17 de octubre de 2026", "Integración multitenant en el requerimiento, alcance, actores, requisitos, reglas, modelo, diccionario, seguridad y criterios de aceptación. Diagramas sin cambios."),
    ], [52, 131, CONTENT_W - 183])]
    s += [Spacer(1, 54), SignatureBlock()]
    return fragment(s)


class SignatureBlock(Flowable):
    """Show only the responsible author's existing handwritten signature."""

    def __init__(self):
        super().__init__()
        self.width = CONTENT_W
        self.height = 150

    def draw(self):
        signature = PdfReader(SOURCE).pages[12].images[0]
        c = self.canv
        c.drawImage(ImageReader(BytesIO(signature.data)), 78, 51, width=58, height=91, mask="auto")
        c.setStrokeColor(colors.black)
        c.line(10, 49, 231, 49)
        c.line(305, 49, 526, 49)
        c.setFont("Arial-Bold", 9.4)
        c.drawCentredString(120, 30, "JAIME CARDONA")
        c.drawCentredString(415, 30, "HENDRICK CALDERON")
        c.setFont("Arial", 9.1)
        c.drawCentredString(120, 16, "Responsable del proyecto")
        c.drawCentredString(415, 16, "Ingeniero revisor")


def single_page(draw) -> PdfReader:
    stream = BytesIO()
    c = canvas.Canvas(stream, pagesize=letter)
    draw(c)
    c.showPage()
    c.save()
    stream.seek(0)
    return PdfReader(stream)


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
            ("Versión", "1.2"),
            ("Base de datos", "PostgreSQL"),
            ("Responsable", "Jaime Cardona"),
            ("Revisor", "Hendrick Calderon"),
            ("Fecha", "17 de octubre de 2026"),
        ]
        x, y, h = LEFT, 628, 23
        c.setStrokeColor(colors.HexColor("#696969"))
        for label, value in info:
            c.rect(x, y - h, CONTENT_W, h)
            c.line(x + CONTENT_W / 2, y, x + CONTENT_W / 2, y - h)
            c.setFont("Arial-Bold", 9.2)
            c.drawString(x + 6, y - 15, label)
            c.setFont("Arial", 9.0)
            c.drawString(x + CONTENT_W / 2 + 6, y - 15, value)
            y -= h
        c.setFillColor(colors.HexColor("#e7e7e7"))
        c.rect(x, 403, 150, 42, fill=1, stroke=1)
        c.setFillColor(colors.black)
        c.rect(x + 150, 403, CONTENT_W - 150, 42, fill=0, stroke=1)
        c.setFont("Arial-Bold", 10)
        c.drawCentredString(x + 75, 420, "Objetivo central")
        c.setFont("Arial", 9.5)
        c.drawCentredString(x + 150 + (CONTENT_W - 150) / 2, 428, "Decidir y registrar el acceso de un miembro")
        c.drawCentredString(x + 150 + (CONTENT_W - 150) / 2, 414, "sin mezclar datos de gimnasios distintos.")
    return single_page(draw)


def index(starts):
    def draw(c):
        c.setFont("Arial-Bold", 14)
        c.drawString(LEFT, 723, "Índice")
        items = [
            ("1. Propósito y requerimiento del sistema", "intro"),
            ("2. Alcance del MVP", "intro"),
            ("3. Actores", "intro"),
            ("4. Modelado del sistema", "diagram"),
            ("   4.1 Diagrama de Contexto", "diagram"),
            ("   4.2 Diagrama de Flujo de Datos - Nivel 0", "diagram2"),
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
            c.setFont("Arial-Bold" if bold else "Arial", 9.8)
            c.drawString(LEFT + 7, y, label)
            c.drawRightString(PAGE_W - RIGHT - 7, y, str(starts[key]))
            y -= 28 if bold else 24
    return single_page(draw)


def footer(page, page_number):
    stream = BytesIO()
    c = canvas.Canvas(stream, pagesize=letter)
    c.setFillColor(colors.white)
    c.rect(0, 0, PAGE_W, 56, fill=1, stroke=0)
    c.setFillColor(colors.black)
    if page_number not in (1, 4, 5):
        c.setFont("Arial-Bold", 9)
        c.drawString(LEFT, 764, "DERCAS - Sistema de Gestión de Gimnasio")
    c.setFont("Arial", 9)
    c.drawRightString(PAGE_W - RIGHT, 25, f"Versión 1.2 | Página {page_number}")
    c.save()
    stream.seek(0)
    page.merge_page(PdfReader(stream).pages[0])


def main():
    original = PdfReader(SOURCE)
    parts = {
        "intro": intro(),
        "functional": functional(),
        "rules": rules(),
        "model": model(),
        "dictionary": dictionary(),
        "nonfunctional": nonfunctional(),
        "acceptance": acceptance(),
        "revisions": revisions(),
    }
    starts = {"intro": 3}
    number = 3 + len(parts["intro"].pages)
    starts["diagram"] = number
    starts["diagram2"] = number + 1
    number += 2
    for key in ["functional", "rules", "model", "dictionary", "nonfunctional", "acceptance", "revisions"]:
        starts[key] = number
        number += len(parts[key].pages)

    writer = PdfWriter()
    writer.add_page(cover().pages[0])
    writer.add_page(index(starts).pages[0])
    for page in parts["intro"].pages:
        writer.add_page(page)
    for i in (3, 4):
        writer.add_page(original.pages[i])
    for key in ["functional", "rules", "model", "dictionary", "nonfunctional", "acceptance", "revisions"]:
        for page in parts[key].pages:
            writer.add_page(page)
    for i, page in enumerate(writer.pages, 1):
        footer(page, i)
    writer.add_metadata({"/Title": "DERCAS - Sistema de Gestión de Gimnasio - MVP v1.2", "/Author": "Jaime Cardona"})
    with OUTPUT.open("wb") as out:
        writer.write(out)
    print(f"Wrote {OUTPUT} ({len(writer.pages)} pages)")
    print("Section starts:", starts)


if __name__ == "__main__":
    main()
