package com.syncro.presentation.legal

/*
 * Textos legales de la app. Describen lo que hace el código de verdad: si cambia cómo se tratan
 * los datos (un servidor propio, analítica, IA con Gemini, otro permiso de Google…), hay que
 * actualizar la política de privacidad ANTES de publicar ese cambio, y su fecha.
 *
 * Son un borrador redactado a partir del código: conviene que los revise un profesional antes de
 * publicar la app.
 */

/**
 * Datos del titular de la app, que exigen la LSSI (aviso legal) y el RGPD (responsable del
 * tratamiento). Mientras tengan "[…]" la app no está lista para publicarse.
 */
object LegalInfo {
    const val APP_NAME = "Syncro"
    const val OWNER_NAME = "[Nombre y apellidos o razón social del titular]"
    const val OWNER_ADDRESS = "[Domicilio]"
    const val CONTACT_EMAIL = "[Correo de contacto]"
    /** Fecha de la última revisión de los textos; cambiarla al modificarlos. */
    const val LAST_UPDATED = "4 de octubre de 2026"

    val isComplete: Boolean
        get() = listOf(OWNER_NAME, OWNER_ADDRESS, CONTACT_EMAIL).none { it.startsWith("[") }
}

enum class LegalDocumentId(val title: String, val summary: String) {
    PRIVACY("Política de privacidad", "Qué datos usa Syncro y dónde se guardan"),
    TERMS("Términos de uso", "Las condiciones para usar la app"),
    LEGAL_NOTICE("Aviso legal", "Quién es el titular de la app"),
    OPEN_SOURCE("Licencias de código abierto", "Librerías y fuentes que usa Syncro")
}

/** Un apartado de un documento: título y párrafos. Los que empiezan por "• " se ven como viñetas. */
data class LegalSection(val heading: String, val paragraphs: List<String>)

data class LegalDocument(val id: LegalDocumentId, val intro: String, val sections: List<LegalSection>)

/** Un componente de terceros incluido en la app y el archivo con su licencia (en assets). */
data class OpenSourceComponent(val name: String, val author: String, val license: String, val licenseFile: String)

private const val APACHE = "licenses/Apache-2.0.txt"

val openSourceComponents = listOf(
    OpenSourceComponent("Android Jetpack (AndroidX, Jetpack Compose, Material 3, Room, WorkManager, DataStore, Navigation, Credential Manager, Glance)", "The Android Open Source Project", "Apache License 2.0", APACHE),
    OpenSourceComponent("Kotlin y kotlinx.coroutines", "JetBrains s.r.o. y colaboradores", "Apache License 2.0", APACHE),
    OpenSourceComponent("Dagger / Hilt", "The Dagger Authors", "Apache License 2.0", APACHE),
    OpenSourceComponent("Media3 (ExoPlayer)", "The Android Open Source Project", "Apache License 2.0", APACHE),
    OpenSourceComponent("Coil", "Coil Contributors", "Apache License 2.0", APACHE),
    OpenSourceComponent("OkHttp", "Square, Inc.", "Apache License 2.0", APACHE),
    OpenSourceComponent("Google API Client Library y Google Calendar / Tasks API para Java", "Google LLC", "Apache License 2.0", APACHE),
    OpenSourceComponent("Google Auth Library para Java", "Google Inc.", "BSD 3-Clause", "licenses/BSD-3-Clause-google-auth-library.txt"),
    OpenSourceComponent("Calendar (kizitonwose)", "Kizito Nwose", "MIT License", "licenses/MIT-kizitonwose-Calendar.txt"),
    OpenSourceComponent("Plus Jakarta Sans (fuente)", "The Plus Jakarta Sans Project Authors", "SIL Open Font License 1.1", "licenses/OFL-PlusJakartaSans.txt"),
    OpenSourceComponent("Fraunces (fuente)", "The Fraunces Project Authors", "SIL Open Font License 1.1", "licenses/OFL-Fraunces.txt")
)

fun legalDocument(id: LegalDocumentId): LegalDocument = when (id) {
    LegalDocumentId.PRIVACY -> privacyPolicy
    LegalDocumentId.TERMS -> termsOfUse
    LegalDocumentId.LEGAL_NOTICE -> legalNotice
    LegalDocumentId.OPEN_SOURCE -> openSourceNotice
}

private val owner = "${LegalInfo.OWNER_NAME}, con domicilio en ${LegalInfo.OWNER_ADDRESS}"

// region Política de privacidad

private val privacyPolicy = LegalDocument(
    id = LegalDocumentId.PRIVACY,
    intro = "Esta política explica qué datos personales usa ${LegalInfo.APP_NAME}, para qué y qué puedes hacer con ellos. " +
        "Última actualización: ${LegalInfo.LAST_UPDATED}.",
    sections = listOf(
        LegalSection(
            "En pocas palabras",
            listOf(
                "• Tus datos se guardan en tu móvil y en tu propia cuenta de Google. ${LegalInfo.APP_NAME} no tiene servidores propios: el titular de la app no recibe ni puede ver tus tareas, eventos, notas, ingresos y gastos ni datos de tu cuenta.",
                "• La app solo se comunica con Google, para iniciar sesión y para sincronizar Google Calendar y Google Tasks.",
                "• No hay publicidad, no se venden ni se ceden datos, y no se usan herramientas de analítica ni de seguimiento.",
                "• Los avisos diarios, los avisos de tus tareas y eventos y el asistente funcionan dentro del móvil, sin enviar tus datos a ningún sitio."
            )
        ),
        LegalSection(
            "Responsable del tratamiento",
            listOf("$owner. Correo de contacto: ${LegalInfo.CONTACT_EMAIL}.")
        ),
        LegalSection(
            "Qué datos se usan",
            listOf(
                "• Datos de tu cuenta de Google: nombre, correo electrónico, foto de perfil y los identificadores de acceso que Google entrega al iniciar sesión.",
                "• Eventos de Google Calendar: título, fechas y horas, descripción, ubicación, subtareas y la información de categoría y color que añades en la app. Si le pones un aviso en la app, a Google se le indica que no avise de ese evento (para que no te lleguen dos); el aviso en sí no se envía.",
                "• Tareas de Google Tasks: título, notas, fecha, hora (solo en la app) y si están hechas.",
                "• Notas: solo se guardan en tu móvil; no se sincronizan con Google ni con ningún otro servicio.",
                "• Ingresos, gastos y presupuestos (sección Ahorros): importe, tipo, categoría, fecha, nota, si se repite cada mes y el límite mensual que pongas a cada categoría. Los apuntas tú; solo se guardan en tu móvil, no se sincronizan con Google ni con ningún otro servicio y la app no se conecta a tu banco.",
                "• Uso de la app en el móvil: tus respuestas al asistente, las prioridades que eliges cada día, tus ajustes (horarios de los avisos, día de cobro de la nómina si lo indicas, tema) y si has dado permiso de notificaciones."
            )
        ),
        LegalSection(
            "Para qué se usan",
            listOf(
                "• Mostrar en un solo sitio tus tareas, eventos y notas, llevar la cuenta de tus ingresos y gastos (totales del mes y tasa de ahorro, calculados en el móvil), y mantener tus tareas y eventos sincronizados con tu cuenta de Google en los dos sentidos (los cambios que haces en la app se guardan en Google, y al revés).",
                "• Enviarte, si lo activas, los avisos diarios de la mañana y de la noche, que se generan en el móvil a partir de los datos guardados en él.",
                "• Avisarte de una tarea o un evento a la hora que elijas, con una notificación que se programa en el móvil.",
                "• Que el asistente te proponga qué hacer con las tareas pendientes y te pregunte por tus prioridades, también calculado en el móvil.",
                "No se crean perfiles, no se toman decisiones automatizadas con efectos jurídicos sobre ti y no se usan tus datos con fines publicitarios."
            )
        ),
        LegalSection(
            "Base legal",
            listOf(
                "• Prestarte el servicio que pides al usar la app (artículo 6.1.b del Reglamento General de Protección de Datos): iniciar sesión, mostrar y sincronizar tus tareas y eventos, y guardar tus notas, ingresos, gastos y ajustes.",
                "• Tu consentimiento (artículo 6.1.a) para el acceso a tu Google Calendar y Google Tasks, que concedes en la pantalla de permisos de Google, y para las notificaciones, que concedes en el aviso de Android. Puedes retirarlo en cualquier momento (ver \"Tus derechos\")."
            )
        ),
        LegalSection(
            "Con quién se comparten",
            listOf(
                "Solo con Google, que es quien presta el inicio de sesión y los servicios de Calendar y Tasks, y con quien ya tienes una relación como usuario. Google trata esos datos según su propia política de privacidad (policies.google.com/privacy).",
                "Google puede tratar datos fuera del Espacio Económico Europeo, con las garantías que recoge su política, como el Marco de Privacidad de Datos UE-EE. UU. y las cláusulas contractuales tipo de la Comisión Europea.",
                "Tickets y resúmenes: si compartes el ticket de un ingreso o gasto o el resumen de un mes, se crea una imagen con sus datos en el almacenamiento temporal de la app y se envía solo a la app que elijas en el menú de compartir de Android (por ejemplo WhatsApp o el correo), que la tratará según su propia política. La app no la envía a ningún otro sitio.",
                "Exportaciones y copias: si exportas tus ingresos y gastos o haces una copia de seguridad, el archivo se guarda solo donde tú elijas (tu móvil, Google Drive u otro servicio que tengas instalado), que lo tratará según su propia política. Restaurar una copia solo lee el archivo que elijas.",
                "Copias de seguridad: si tienes activada la copia de seguridad de Android, el sistema puede incluir los datos de la app (entre ellos las notas, los ingresos y gastos y la sesión) en la copia de tu cuenta de Google. Puedes desactivarla en los ajustes del móvil."
            )
        ),
        LegalSection(
            "Uso de los datos de Google",
            listOf(
                "El uso que ${LegalInfo.APP_NAME} hace de la información recibida de las API de Google, y su transferencia a cualquier otra aplicación, se ajusta a la Política de datos de usuario de los servicios de API de Google (Google API Services User Data Policy), incluidos los requisitos de Uso limitado (Limited Use).",
                "• La app pide acceso a Google Calendar y Google Tasks solo para mostrarte y sincronizar tus eventos y tareas.",
                "• Esos datos no se transfieren a terceros, salvo a Google para la propia sincronización o cuando lo exija la ley.",
                "• No se usan para publicidad ni para entrenar modelos de inteligencia artificial, y ninguna persona los lee."
            )
        ),
        LegalSection(
            "Cuánto tiempo se guardan",
            listOf(
                "• En tu móvil, mientras uses la app. Al cerrar sesión se borran del móvil tus tareas, eventos, notas, ingresos y gastos, el chat del asistente y las prioridades (la app te avisa antes de lo que no se podrá recuperar, como las notas o los ingresos y gastos). Al desinstalar la app, Android borra todos sus datos.",
                "• En tu cuenta de Google, tus eventos y tareas siguen allí hasta que los borres, según las condiciones de Google."
            )
        ),
        LegalSection(
            "Tus derechos",
            listOf(
                "Puedes ejercer tus derechos de acceso, rectificación, supresión, oposición, limitación del tratamiento y portabilidad. Como tus datos están en tu móvil y en tu cuenta de Google, la mayoría los controlas directamente:",
                "• Ver, corregir o borrar tus tareas, eventos, notas, ingresos y gastos desde la propia app, o tus eventos y tareas desde Google Calendar y Google Tasks.",
                "• Llevarte tus datos (portabilidad): en Ajustes > Tus datos puedes exportar tus ingresos y gastos (CSV) y hacer una copia de tus notas, ingresos, gastos y presupuestos.",
                "• Borrar los datos del móvil cerrando sesión (Ajustes > Cuenta) o desinstalando la app.",
                "• Retirar el acceso de ${LegalInfo.APP_NAME} a tu cuenta de Google en myaccount.google.com/permissions.",
                "• Retirar el permiso de notificaciones en Ajustes > Notificaciones o en los ajustes del móvil.",
                "Para cualquier duda o solicitud, escribe a ${LegalInfo.CONTACT_EMAIL}. Si crees que no se han respetado tus derechos, puedes reclamar ante la Agencia Española de Protección de Datos (www.aepd.es)."
            )
        ),
        LegalSection(
            "Seguridad",
            listOf(
                "Los datos se guardan en el almacenamiento privado de la app, al que otras apps no pueden acceder, y la comunicación con Google se hace siempre cifrada (HTTPS). Protege tu móvil con bloqueo de pantalla para que nadie más pueda abrir la app."
            )
        ),
        LegalSection(
            "Menores de edad",
            listOf(
                "${LegalInfo.APP_NAME} no está dirigida a menores de 14 años. Si tienes menos de 14 años, necesitas el consentimiento de tus padres o tutores para usarla."
            )
        ),
        LegalSection(
            "Cambios en esta política",
            listOf(
                "Si cambia la forma en que la app usa tus datos, se actualizará esta política y su fecha. Si el cambio es importante, se te avisará dentro de la app antes de que se aplique."
            )
        )
    )
)

// endregion

// region Términos de uso

private val termsOfUse = LegalDocument(
    id = LegalDocumentId.TERMS,
    intro = "Estos términos regulan el uso de ${LegalInfo.APP_NAME}. Al usar la app los aceptas; si no estás de acuerdo, no la uses. " +
        "Última actualización: ${LegalInfo.LAST_UPDATED}.",
    sections = listOf(
        LegalSection(
            "El servicio",
            listOf(
                "${LegalInfo.APP_NAME} es una app gratuita que reúne tus tareas, eventos y notas, te permite apuntar tus ingresos y gastos, se sincroniza con tu cuenta de Google (Google Calendar y Google Tasks) y te ofrece avisos y un asistente para organizar tu día. La ofrece $owner (ver el Aviso legal)."
            )
        ),
        LegalSection(
            "Tu cuenta de Google",
            listOf(
                "Para usar la app necesitas una cuenta de Google. Tu uso de los servicios de Google se rige por las condiciones de Google (policies.google.com/terms). Eres responsable de tu cuenta y de lo que se haga con ella en tu móvil."
            )
        ),
        LegalSection(
            "Uso adecuado",
            listOf(
                "Te comprometes a usar la app de forma lícita y a no intentar dañarla, alterar su funcionamiento ni acceder a datos de otras personas."
            )
        ),
        LegalSection(
            "Tus datos y copias",
            listOf(
                "Tus tareas y eventos se guardan en tu cuenta de Google. Las notas y los ingresos y gastos solo se guardan en tu móvil: se pierden si cierras sesión, desinstalas la app o pierdes el móvil, así que te recomendamos no guardar en ellos nada que no quieras perder. El tratamiento de tus datos se explica en la Política de privacidad."
            )
        ),
        LegalSection(
            "Disponibilidad y responsabilidad",
            listOf(
                "La app se ofrece tal cual y de forma gratuita. Se intenta que funcione bien, pero no se garantiza que esté libre de errores ni que esté siempre disponible; la sincronización depende además de los servicios de Google y de tu conexión.",
                "La sección Ahorros es solo una libreta para apuntar tus ingresos y gastos: no está conectada a tu banco, no mueve dinero y sus cifras y totales no son asesoramiento financiero. Comprueba siempre tus saldos reales con tu entidad.",
                "En la medida en que lo permita la ley, el titular no responde de daños derivados de errores, interrupciones o pérdidas de datos. Nada de lo anterior limita los derechos que te reconoce la normativa de consumidores ni la responsabilidad que no puede excluirse legalmente."
            )
        ),
        LegalSection(
            "Propiedad intelectual",
            listOf(
                "La app, su diseño y sus textos pertenecen a su titular. Los componentes de terceros (librerías y fuentes) se usan según sus licencias, que puedes consultar en Licencias de código abierto."
            )
        ),
        LegalSection(
            "Dejar de usar la app",
            listOf(
                "Puedes dejar de usarla cuando quieras cerrando sesión o desinstalándola, y retirar su acceso a tu cuenta en myaccount.google.com/permissions."
            )
        ),
        LegalSection(
            "Cambios en estos términos",
            listOf(
                "Estos términos pueden actualizarse. Si el cambio es importante, se te avisará dentro de la app; si sigues usándola después, se entiende que aceptas la nueva versión."
            )
        ),
        LegalSection(
            "Ley aplicable",
            listOf(
                "Estos términos se rigen por la ley española. Si eres consumidor, podrás acudir a los tribunales de tu domicilio y conservas la protección que te den las leyes de tu país de residencia."
            )
        )
    )
)

// endregion

// region Aviso legal

private val legalNotice = LegalDocument(
    id = LegalDocumentId.LEGAL_NOTICE,
    intro = "En cumplimiento de la Ley 34/2002, de servicios de la sociedad de la información y de comercio electrónico (LSSI-CE), " +
        "estos son los datos del titular de ${LegalInfo.APP_NAME}.",
    sections = listOf(
        LegalSection(
            "Titular",
            listOf(
                "• Nombre: ${LegalInfo.OWNER_NAME}",
                "• Domicilio: ${LegalInfo.OWNER_ADDRESS}",
                "• Correo electrónico: ${LegalInfo.CONTACT_EMAIL}"
            )
        ),
        LegalSection(
            "Objeto",
            listOf(
                "${LegalInfo.APP_NAME} es una aplicación gratuita para organizar tareas, eventos y notas. Su uso se rige por los Términos de uso y el tratamiento de datos por la Política de privacidad."
            )
        ),
        LegalSection(
            "Propiedad intelectual e industrial",
            listOf(
                "Los derechos sobre la app, su diseño, sus textos y su código pertenecen a su titular, salvo los componentes de terceros, que se usan según sus licencias. Google, Google Calendar y Google Tasks son marcas de Google LLC; su mención no implica patrocinio ni respaldo."
            )
        ),
        LegalSection(
            "Contacto",
            listOf("Para cualquier consulta sobre la app o estos textos, escribe a ${LegalInfo.CONTACT_EMAIL}.")
        )
    )
)

// endregion

// region Código abierto

private val openSourceNotice = LegalDocument(
    id = LegalDocumentId.OPEN_SOURCE,
    intro = "${LegalInfo.APP_NAME} se ha hecho con estos componentes de terceros. Gracias a sus autores. Toca uno para leer su licencia completa.",
    sections = emptyList()
)

// endregion
