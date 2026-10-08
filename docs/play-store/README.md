# Publicar Syncro en Google Play — lo legal y los formularios

Guía para rellenar Play Console y la verificación de Google con lo que hace la app de verdad
(revisada contra el código el 4 de octubre de 2026). Si cambia cómo se tratan los datos, hay que
actualizar `LegalDocuments.kt`, regenerar `docs/legal` y repasar esta guía.

> Es una guía práctica, no asesoramiento jurídico. Conviene que un profesional revise los textos
> antes de publicar.

## 1. Antes de nada: datos del titular

Ya están en `app/src/main/java/com/syncro/presentation/legal/LegalDocuments.kt` (`LegalInfo`):
nombre y apellidos, domicilio y correo de contacto (`LegalDocumentsTest` falla si vuelve a quedar
algún `[…]`). La LSSI exige que aparezcan en el aviso legal y el RGPD en la política de privacidad.
El NIF no se publica a propósito. Si cambia algún dato, regenerar las páginas web:

```
UPDATE_LEGAL_HTML=true ./gradlew testDebugUnitTest --tests "*LegalHtmlTest"
```

## 2. Publicar la política de privacidad (URL pública)

Play Console y la pantalla de consentimiento de Google piden una URL pública. Las páginas están en
`docs/legal/` (`privacidad.html`, `terminos.html`, `aviso-legal.html`, `licencias.html`).

- Opción sencilla: GitHub Pages sirviendo la carpeta `docs/` (el repositorio tiene que ser
  público, o tener un plan que permita Pages en privados). La URL quedará como
  `https://<usuario>.github.io/<repo>/legal/privacidad.html`.
- Para la verificación de Google (punto 5) el dominio de la política tiene que estar verificado en
  Google Search Console como tuyo. Un `usuario.github.io` se puede verificar; un dominio propio
  también.

## 3. Seguridad de los datos (Data safety)

Lo que el código hace hoy:

| Dato | ¿Sale del móvil? | A dónde | Para qué |
| --- | --- | --- | --- |
| Nombre, correo y foto de Google | Sí (inicio de sesión) | Google | Gestión de la cuenta |
| Eventos de Calendar (título, horas, descripción, ubicación) | Sí | Google Calendar (tu cuenta) | Funcionalidad |
| Tareas de Google Tasks (título, notas, fecha, hecha) | Sí | Google Tasks (tu cuenta) | Funcionalidad |
| Notas, ingresos, gastos, presupuestos, cuentas de ahorro, repeticiones, avisos, ajustes, chat del asistente | **No** | Solo en el móvil | — |

Respuestas recomendadas:

- **¿Recoge o comparte datos?** Sí, recoge (salen del móvil hacia Google para que funcione la app).
- **Información personal → Nombre y Correo electrónico**: recogidos, no compartidos, obligatorios,
  finalidad *Funcionalidad de la app* y *Gestión de la cuenta*.
- **Calendario → Eventos del calendario**: recogidos, no compartidos, obligatorios, finalidad
  *Funcionalidad de la app*.
- **Actividad en apps / Otro contenido generado por el usuario** (las tareas de Google Tasks):
  igual que los eventos.
- **Información financiera: NO declarar como recogida**: ingresos y gastos no salen del móvil.
- **Cifrado en tránsito**: Sí (HTTPS con las API de Google).
- **¿Pueden pedir que se borren?** Sí: cerrando sesión o desinstalando (borra todo lo del móvil) y
  quitando el acceso en myaccount.google.com/permissions. Está explicado en la política,
  apartado «Cómo borrar tus datos».
- **Eliminación de cuenta**: la app no crea cuentas propias (usa la de Google y no guarda nada en
  servidores propios), así que no aplica el requisito de borrar una cuenta de la app. Si Play lo
  pide, enlaza el apartado «Cómo borrar tus datos» de la política.
- **Publicidad / analítica / SDK de terceros que recojan datos**: ninguno.

## 4. Permisos sensibles

- **`USE_EXACT_ALARM`** (alarmas exactas): Play solo lo permite a apps cuya función principal sea de
  alarma o calendario. Syncro es una app de calendario y tareas con avisos a una hora concreta:
  rellena la declaración de «Alarmas exactas» en Play Console explicándolo (resúmenes diarios y
  avisos de eventos/tareas a la hora elegida). Si Google lo rechazara, la alternativa es
  `SCHEDULE_EXACT_ALARM` con su pantalla de permiso, y el código ya cae a una ventana corta si no
  hay alarma exacta.
- **Notificaciones** (`POST_NOTIFICATIONS`): se piden en la guía de inicio, el asistente o Ajustes,
  no al abrir la app.
- `RECEIVE_BOOT_COMPLETED`, `INTERNET`, `ACCESS_NETWORK_STATE`: normales, sin declaración.

## 5. Verificación de Google (OAuth) — imprescindible

La app pide los ámbitos `https://www.googleapis.com/auth/calendar` y
`https://www.googleapis.com/auth/tasks`, que Google considera **sensibles**. Sin verificar la app
en Google Cloud Console, los usuarios ven «Google no ha verificado esta app» y hay un límite de
100 usuarios. Para verificarla:

1. Pantalla de consentimiento OAuth: nombre de la app, logo, correo de asistencia, dominio
   autorizado, **URL de la página de inicio** y **URL de la política de privacidad** (la del punto 2,
   en un dominio verificado).
2. Justificar cada ámbito: Calendar para leer y escribir los eventos del usuario; Tasks para leer
   y escribir sus tareas. Es la función principal de la app.
3. Un vídeo (YouTube, puede ser oculto) que muestre el inicio de sesión, la pantalla de permisos de
   Google y cómo se usan los eventos y las tareas en la app.
4. La política ya incluye la cláusula de **Uso limitado** que Google exige (apartado «Uso de los
   datos de Google»).
5. Registrar también el SHA-1 de la clave de firma de la versión de Play (Play App Signing) en el
   cliente OAuth de Android.

## 6. Resto de la ficha

- **Acceso a la app**: hace falta iniciar sesión con Google. Facilita a los revisores una cuenta de
  Google de pruebas (con algún evento y tarea) en «Acceso a la app».
- **Anuncios**: No contiene anuncios.
- **Público objetivo**: mayores de edad (o 16+). No está dirigida a niños; la política indica que
  los menores de 14 necesitan consentimiento de sus padres.
- **Clasificación de contenido**: cuestionario IARC; la app no tiene violencia, apuestas, chats
  entre usuarios ni compras. Herramienta de productividad.
- **App de finanzas**: Syncro no presta servicios financieros (no conecta con bancos, no da
  préstamos ni mueve dinero). En la declaración de «Funciones financieras», marca que no ofrece
  ninguna de las funciones reguladas.
- **Categoría**: Productividad.

## 7. Recomendación técnica pendiente

`res/xml/backup_rules.xml` y `data_extraction_rules.xml` solo excluyen los ajustes (`app_settings`,
para que la guía de inicio vuelva a salir al reinstalar): la copia de seguridad de Android incluye
toda la base de datos, también la sesión de Google. La política lo
dice, pero sería más seguro excluir la sesión (`user_profile`) de la copia en la nube.
