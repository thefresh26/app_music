# Pentagrama 🎼

Aplicación de música **multiplataforma (Android + PC)** hecha con **Kotlin Multiplatform + Compose Multiplatform** para:

- **Escribir partituras y cifrados** como los que se hacen a mano: notas, silencios, barras rítmicas (`////`), acordes (Am, F#m7b5, Bb/D…), etiquetas de sección resaltadas (Intro, Estrofa, Coro, Puente…), repeticiones, casillas 1ª/2ª, `%`, Segno, Coda, D.S. al Coda, `x2`, dinámicas, articulaciones y ornamentos.
- **Buscar cualquier figura musical** con un filtro (sin importar tildes): "negra", "calderón", "clave de fa", "sostenido", "coda"… Además de la paleta curada en español, incluye **las ~2.900 figuras del estándar SMuFL** (fuente Bravura) en la categoría *Todos (SMuFL)*.
- **Llenar el pentagrama automáticamente desde un MP3** (o WAV/M4A/OGG en Android): detecta **tempo**, **tonalidad**, **melodía** (notas y ritmo cuantizado) y **acordes** (cifrado), todo **en el dispositivo, sin internet**.
- Guardar, abrir y **exportar a MusicXML** (para abrir en MuseScore, Finale, Sibelius…).

## Cómo usarla

1. **Nueva partitura** → elige plantilla (en blanco, cifrado o canción con secciones), clave, tonalidad y compás.
2. En la **paleta** elige una figura (o búscala). Luego **toca el pentagrama**:
   - Notas: la altura depende de dónde toques (líneas/espacios). La armadura se aplica sola.
   - *Modo acorde*: tocar sobre una nota existente le agrega otra nota (acorde).
   - Alteraciones, puntillo, ligadura, tresillo, articulaciones, dinámicas: toca la nota (o selecciónala y toca la figura en la paleta).
   - Barras, repeticiones, casillas, Segno/Coda, `%`: toca el compás.
   - Acorde / sección / texto: toca el lugar y escribe (hay sugerencias rápidas).
   - Borrar: herramienta *Borrar* o botón 🗑 con algo seleccionado.
3. Botones: ↑ ↓ mueven la nota seleccionada, ◀ ▶ recorren notas, +/− compás, zoom, deshacer/rehacer.
4. **Desde MP3**: en el inicio (o en el menú ⋮ del editor) → elige el archivo → modo (*Melodía + cifrado*, *Solo melodía* o *Solo cifrado*) → opcionalmente fija el tempo o el compás.

> La transcripción automática es monofónica para la melodía (sigue la voz/instrumento principal) y estima los acordes de la mezcla completa. Da un excelente punto de partida, pero conviene revisarla en el editor.

## Compilar

Requisitos: JDK 17+, Android Studio (Koala o superior) o IntelliJ IDEA.

```bash
# PC (Windows / macOS / Linux)
./gradlew :composeApp:run

# Instalador de escritorio (.msi / .dmg / .deb)
./gradlew :composeApp:packageDistributionForCurrentOS

# APK Android (debug)
./gradlew :composeApp:assembleDebug
# -> composeApp/build/outputs/apk/debug/composeApp-debug.apk

# Pruebas de la lógica (transcripción, modelo, layout)
./gradlew :composeApp:desktopTest
```

En Windows usa `gradlew.bat` en lugar de `./gradlew`.

**GitHub Actions** compila todo en cada `push`: descarga el APK y la app de escritorio (ZIP portable para Windows, macOS y Linux) desde la pestaña *Actions → Build → Artifacts*. Si creas un tag `v1.0.0`, se publica una *Release* con los archivos.

## Estructura

```
composeApp/src/
├─ commonMain/            # Código compartido (95 %)
│  ├─ model/              # Partitura, JSON, MusicXML, plantillas
│  ├─ symbols/            # Catálogo de figuras + buscador, glifos SMuFL
│  ├─ editor/             # Operaciones de edición, deshacer/rehacer
│  ├─ render/             # Layout y dibujo del pentagrama (Canvas)
│  ├─ audio/              # Transcripción: YIN, tempo, tonalidad, acordes, FFT
│  ├─ ui/                 # Pantallas (biblioteca, editor, paleta, diálogos)
│  └─ composeResources/   # Fuente Bravura (SMuFL) + catálogo smufl.tsv
├─ androidMain/           # Selector de archivos y decodificador MediaCodec
└─ desktopMain/           # Ventana, diálogos de archivo y decodificador MP3 (JLayer)
```

Las partituras se guardan en el almacenamiento interno (Android) o en `~/Pentagrama` (PC).

## Licencias

- Fuente **Bravura** © Steinberg Media Technologies GmbH, licencia SIL Open Font License 1.1 (`composeResources/files/BRAVURA-OFL.txt`).
- **JLayer** (decodificador MP3 de escritorio), LGPL.
