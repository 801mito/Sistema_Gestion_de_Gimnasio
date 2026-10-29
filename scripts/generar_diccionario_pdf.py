"""Genera el PDF del diccionario a partir de su fuente Markdown."""

from __future__ import annotations

import html
import re
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    CondPageBreak,
    KeepTogether,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs" / "diccionario-de-datos-extendido.md"
OUTPUT = ROOT / "docs" / "diccionario-de-datos-extendido.pdf"

CHARCOAL = colors.HexColor("#303030")
MID_GRAY = colors.HexColor("#606060")
PALE_GRAY = colors.HexColor("#F5F5F5")
RULE = colors.HexColor("#D2D2D2")
TEXT = colors.HexColor("#222222")


def register_fonts() -> None:
    fonts = Path("C:/Windows/Fonts")
    pdfmetrics.registerFont(TTFont("Arial", str(fonts / "arial.ttf")))
    pdfmetrics.registerFont(TTFont("Arial-Bold", str(fonts / "arialbd.ttf")))
    pdfmetrics.registerFont(TTFont("Arial-Italic", str(fonts / "ariali.ttf")))
    pdfmetrics.registerFontFamily(
        "Arial", normal="Arial", bold="Arial-Bold", italic="Arial-Italic"
    )


def styles() -> dict[str, ParagraphStyle]:
    base = getSampleStyleSheet()
    return {
        "title": ParagraphStyle(
            "TitleCustom", parent=base["Normal"], fontName="Arial-Bold",
            fontSize=20, leading=24, textColor=TEXT, spaceAfter=13,
        ),
        "subtitle": ParagraphStyle(
            "SubtitleCustom", parent=base["Normal"], fontName="Arial",
            fontSize=10, leading=14, textColor=MID_GRAY, spaceAfter=19,
        ),
        "h2": ParagraphStyle(
            "Heading2Custom", parent=base["Normal"], fontName="Arial-Bold",
            fontSize=13.5, leading=17, textColor=TEXT, spaceBefore=16,
            spaceAfter=8, keepWithNext=True,
        ),
        "h3": ParagraphStyle(
            "Heading3Custom", parent=base["Normal"], fontName="Arial-Bold",
            fontSize=10.5, leading=14, textColor=CHARCOAL, spaceBefore=13,
            spaceAfter=7, keepWithNext=True,
        ),
        "body": ParagraphStyle(
            "BodyCustom", parent=base["Normal"], fontName="Arial",
            fontSize=9, leading=13, textColor=TEXT, spaceAfter=8,
            allowWidows=0, allowOrphans=0,
        ),
        "bullet": ParagraphStyle(
            "BulletCustom", parent=base["Normal"], fontName="Arial",
            fontSize=9, leading=13, textColor=TEXT, leftIndent=13,
            firstLineIndent=-9, spaceAfter=4,
        ),
        "card_title": ParagraphStyle(
            "CardTitleCustom", parent=base["Normal"], fontName="Arial-Bold",
            fontSize=9.2, leading=12, textColor=colors.white,
        ),
        "card_label": ParagraphStyle(
            "CardLabelCustom", parent=base["Normal"], fontName="Arial-Bold",
            fontSize=7.5, leading=10.2, textColor=CHARCOAL,
        ),
        "card_value": ParagraphStyle(
            "CardValueCustom", parent=base["Normal"], fontName="Arial",
            fontSize=8.15, leading=11.1, textColor=TEXT,
        ),
    }


def inline(value: str) -> str:
    """Conserva énfasis, código y enlaces al convertir texto Markdown."""
    value = value.replace("\\|", "|")
    value = value.replace("\u2011", "-").replace("\u2013", "-").replace("\u2014", "-")
    value = html.escape(value)

    def link(match: re.Match[str]) -> str:
        label, url = match.groups()
        return f'<link href="{html.escape(url, quote=True)}" color="#505050">{label}</link>'

    value = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", link, value)
    value = re.sub(r"`([^`]+)`", r'<font name="Courier">\1</font>', value)
    value = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", value)
    value = re.sub(r"\*([^*]+)\*", r"<i>\1</i>", value)
    return value


def split_table_row(line: str) -> list[str]:
    parts = re.split(r"(?<!\\)\|", line.strip())
    return [part.strip().replace("\\|", "|") for part in parts[1:-1]]


def make_card(
    section: int, headers: list[str], values: list[str], sty: dict[str, ParagraphStyle],
    width: float,
) -> KeepTogether:
    if section == 1:
        title = f"{values[0]}  {values[1]}"
        fields = list(zip(headers[2:], values[2:]))
    elif section == 4:
        title = f"Almacén {values[0]}  {values[1]}"
        fields = list(zip(headers[2:], values[2:]))
    else:
        first_name = re.search(r"`([^`]+)`", values[0])
        title = first_name.group(1) if first_name else values[0]
        fields = [(headers[0], values[0])] + list(zip(headers[1:], values[1:]))

    rows: list[list[Paragraph | str]] = [
        [Paragraph(inline(title), sty["card_title"]), ""]
    ]
    for label, value in fields:
        rows.append([
            Paragraph(inline(label), sty["card_label"]),
            Paragraph(inline(value), sty["card_value"]),
        ])

    table = Table(rows, colWidths=[94, width - 94], hAlign="LEFT")
    commands = [
        ("SPAN", (0, 0), (1, 0)),
        ("BACKGROUND", (0, 0), (1, 0), CHARCOAL),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, 0), 7),
        ("BOTTOMPADDING", (0, 0), (-1, 0), 7),
        ("TOPPADDING", (0, 1), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 1), (-1, -1), 4),
        ("LINEBELOW", (0, 0), (-1, -1), 0.3, RULE),
        ("BOX", (0, 0), (-1, -1), 0.45, RULE),
    ]
    for index in range(1, len(rows)):
        if index % 2 == 0:
            commands.append(("BACKGROUND", (0, index), (-1, index), PALE_GRAY))
    table.setStyle(TableStyle(commands))
    return KeepTogether([table, Spacer(1, 9)])


def page_decoration(canvas, doc) -> None:
    canvas.saveState()
    page = canvas.getPageNumber()
    page_width, page_height = A4
    if page > 1:
        canvas.setFont("Arial", 7.3)
        canvas.setFillColor(MID_GRAY)
        canvas.drawString(17 * mm, page_height - 13 * mm,
                          "SISTEMA DE GESTIÓN DE GIMNASIO  |  DICCIONARIO DE DATOS EXTENDIDO")
        canvas.setStrokeColor(RULE)
        canvas.setLineWidth(0.4)
        canvas.line(17 * mm, page_height - 16 * mm,
                    page_width - 17 * mm, page_height - 16 * mm)
    canvas.setStrokeColor(RULE)
    canvas.setLineWidth(0.4)
    canvas.line(17 * mm, 16 * mm, page_width - 17 * mm, 16 * mm)
    canvas.setFont("Arial", 7.3)
    canvas.setFillColor(MID_GRAY)
    canvas.drawString(17 * mm, 12 * mm, "Diccionario de datos extendido")
    canvas.drawRightString(page_width - 17 * mm, 12 * mm, f"Página {page}")
    canvas.restoreState()


def build() -> None:
    register_fonts()
    sty = styles()
    page_width, _ = A4
    margin = 17 * mm
    content_width = page_width - 2 * margin
    doc = SimpleDocTemplate(
        str(OUTPUT), pagesize=A4, leftMargin=margin, rightMargin=margin,
        topMargin=21 * mm, bottomMargin=22 * mm,
        title="Diccionario de datos extendido - Sistema de Gestión de Gimnasio",
        author="Proyecto Sistema de Gestión de Gimnasio",
        subject="Formularios de flujos, estructuras, elementos y almacenes de datos",
    )
    lines = SOURCE.read_text(encoding="utf-8").splitlines()
    story = []
    section = 0
    index = 0
    while index < len(lines):
        line = lines[index].strip()
        if not line:
            index += 1
            continue
        if line.startswith("# "):
            story.append(Paragraph(inline(line[2:]), sty["title"]))
            story.append(Paragraph("Sistema de Gestión de Gimnasio  ·  Formularios completos",
                                   sty["subtitle"]))
            index += 1
            continue
        if line.startswith("## "):
            heading = line[3:]
            match = re.match(r"Formulario (\d+)\.", heading)
            if match:
                section = int(match.group(1))
                story.append(PageBreak())
            story.append(Paragraph(inline(heading), sty["h2"]))
            index += 1
            continue
        if line.startswith("### "):
            story.append(CondPageBreak(75 * mm))
            story.append(Paragraph(inline(line[4:]), sty["h3"]))
            index += 1
            continue
        if line.startswith("|"):
            table_lines = []
            while index < len(lines) and lines[index].strip().startswith("|"):
                table_lines.append(lines[index].strip())
                index += 1
            headers = split_table_row(table_lines[0])
            for row_line in table_lines[2:]:
                values = split_table_row(row_line)
                if len(values) != len(headers):
                    raise ValueError(f"Invalid Markdown row: {row_line[:100]}")
                story.append(make_card(section, headers, values, sty, content_width))
            continue
        if line.startswith("- "):
            parts = [line[2:]]
            index += 1
            while index < len(lines):
                continuation = lines[index]
                if (not continuation.strip() or continuation.startswith("- ")
                        or continuation.startswith("#") or continuation.startswith("|")):
                    break
                parts.append(continuation.strip())
                index += 1
            story.append(Paragraph("- " + inline(" ".join(parts)), sty["bullet"]))
            continue
        parts = [line]
        index += 1
        while index < len(lines):
            continuation = lines[index].strip()
            if (not continuation or continuation.startswith("#")
                    or continuation.startswith("|") or continuation.startswith("- ")):
                break
            parts.append(continuation)
            index += 1
        story.append(Paragraph(inline(" ".join(parts)), sty["body"]))

    doc.build(story, onFirstPage=page_decoration, onLaterPages=page_decoration)
    print(OUTPUT)


if __name__ == "__main__":
    build()
