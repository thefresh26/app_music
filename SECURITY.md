# Seguridad

## Reportar una vulnerabilidad
Escribe a **soporte.tecnico@activosporcolombia.com** con el detalle y los pasos para reproducirla. No abras un *issue* público con información sensible.

## Medidas aplicadas
- **Sin red ni backend**: no hay claves de API, base de datos, cuentas ni sesiones que proteger. Android no pide permiso de Internet y bloquea tráfico en texto plano.
- **Secretos fuera de Git**: `.gitignore` excluye `.env`, keystores (`*.jks`, `*.keystore`, `*.p12`) y archivos de firma. En cada push, **gitleaks** revisa todo el historial.
- **Archivos no confiables** (.pentagrama/.json abiertos por el usuario): límite de 5 MB, profundidad máxima de JSON, números validados, límites de compases, eventos y textos, y rangos saneados (tonalidad, compás, alturas, glifos).
- **Audio**: se decodifica en memoria, con un máximo de 15 minutos para no agotar la RAM del teléfono. Nunca se escribe a disco.
- **Almacenamiento local**: identificadores validados (sin `../`, sin *path traversal*) y guardado atómico (archivo temporal + renombrado).
- **Exportación MusicXML**: todo el texto del usuario se escapa (`& < > "`).
- **Android**: `allowBackup=false`; solo la actividad principal es exportada.
- **CI**: permisos mínimos (`contents: read`; escritura solo para publicar releases), validación del Gradle Wrapper y Dependabot para actualizar dependencias y Actions.

## Firma de la versión de publicación (Play Store)
Nunca subas el keystore. Guárdalo fuera del repo y pásalo a GitHub Actions como *secret* (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`).
