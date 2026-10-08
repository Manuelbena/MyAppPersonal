package com.syncro.presentation.legal

/*
 * Los textos legales como páginas web, para publicarlos (Google Play pide la política de privacidad
 * en una URL pública). Salen de los mismos textos que se ven en la app, así nunca dicen cosas
 * distintas: LegalHtmlTest comprueba que los archivos de docs/legal están al día.
 */

/** Nombre del archivo publicado de cada documento. */
val LegalDocumentId.fileName: String
    get() = when (this) {
        LegalDocumentId.PRIVACY -> "privacidad.html"
        LegalDocumentId.TERMS -> "terminos.html"
        LegalDocumentId.LEGAL_NOTICE -> "aviso-legal.html"
        LegalDocumentId.OPEN_SOURCE -> "licencias.html"
    }

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** El documento como página web independiente, legible en móvil y con modo oscuro. */
fun legalDocumentHtml(id: LegalDocumentId): String {
    val document = legalDocument(id)
    val body = buildString {
        appendLine("<h1>${id.title.escapeHtml()}</h1>")
        appendLine("<p class=\"intro\">${document.intro.escapeHtml()}</p>")
        document.sections.forEach { section ->
            appendLine("<h2>${section.heading.escapeHtml()}</h2>")
            var inList = false
            section.paragraphs.forEach { paragraph ->
                val bullet = paragraph.startsWith("• ")
                if (bullet && !inList) appendLine("<ul>").also { inList = true }
                if (!bullet && inList) appendLine("</ul>").also { inList = false }
                if (bullet) appendLine("<li>${paragraph.removePrefix("• ").escapeHtml()}</li>")
                else appendLine("<p>${paragraph.escapeHtml()}</p>")
            }
            if (inList) appendLine("</ul>")
        }
        if (id == LegalDocumentId.OPEN_SOURCE) {
            appendLine("<ul>")
            openSourceComponents.forEach { component ->
                appendLine("<li><strong>${component.name.escapeHtml()}</strong> · ${component.author.escapeHtml()} · ${component.license.escapeHtml()}</li>")
            }
            appendLine("</ul>")
        }
        appendLine("<nav>")
        LegalDocumentId.entries.filter { it != id }.forEach { other ->
            appendLine("<a href=\"${other.fileName}\">${other.title.escapeHtml()}</a>")
        }
        appendLine("</nav>")
    }
    return """
        |<!doctype html>
        |<html lang="es">
        |<head>
        |<meta charset="utf-8">
        |<meta name="viewport" content="width=device-width, initial-scale=1">
        |<title>${id.title.escapeHtml()} · ${LegalInfo.APP_NAME}</title>
        |<style>
        |:root { --bg: #f8fafc; --text: #0f172a; --muted: #475569; --accent: #155e75; --card: #ffffff; }
        |@media (prefers-color-scheme: dark) { :root { --bg: #0f172a; --text: #e2e8f0; --muted: #94a3b8; --accent: #22d3ee; --card: #1e293b; } }
        |body { margin: 0; background: var(--bg); color: var(--text); font: 16px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
        |main { max-width: 760px; margin: 0 auto; padding: 32px 16px 48px; }
        |h1 { font-size: 1.9rem; margin: 0 0 8px; }
        |h2 { font-size: 1.2rem; margin: 28px 0 8px; color: var(--accent); }
        |.intro { color: var(--muted); }
        |ul { padding-left: 1.2rem; }
        |li { margin: 6px 0; }
        |nav { margin-top: 40px; padding-top: 16px; border-top: 1px solid rgba(100,116,139,.3); display: flex; flex-wrap: wrap; gap: 16px; }
        |a { color: var(--accent); }
        |</style>
        |</head>
        |<body>
        |<main>
        |${body.trimEnd()}
        |</main>
        |</body>
        |</html>
        |""".trimMargin()
}
