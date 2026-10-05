## Pruebas de rendimiento

| Prueba | Resultado | Límite |
|---|---|---|
| Memoria de 1 min de audio decodificado | 5.0 MB (máx. 15 min ≈ 76 MB) | < 6 MB/min |
| Pentagrama completo (todos los sonidos) de canción de 3 min | 873 ms (206× tiempo real, 1614 notas) | < 30 000 ms |
| Guardar partitura de 1 000 compases (JSON) | 28 ms, 814 KB | < 1 000 ms |
| Abrir partitura de 1 000 compases (JSON) | 50 ms | < 1 500 ms |
| Exportar MusicXML de 1 000 compases | 28 ms, 1078 KB | < 1 500 ms |
| Transcribir canción de 3 min (melodía + acordes) | 1570 ms (115× tiempo real) | < 30 000 ms |
| Cargar catálogo de 2928 figuras SMuFL | 7 ms | < 500 ms |
| Búsqueda de figuras (por consulta, 3088 figuras) | 1.3 ms | < 50 ms |
| Acomodar partitura de 500 compases / 4 000 notas | 5 ms (500 líneas) | < 300 ms |
| 1 000 toques sobre el pentagrama (hit test) | 7 ms | < 200 ms |
| Preparar el sonido de una nota al escribirla | 0.87 ms | < 20 ms |
| Preparar el sonido de una acorde al escribirla | 1.03 ms | < 20 ms |
| Preparar el sonido de una barra rítmica sin acorde al escribirla | 0.20 ms | < 20 ms |
| Sintetizar 150 compases (5 min 0 s) antes de ▶ | 464 ms | < 2 000 ms |
| ▶ desde el último compás | 3 ms | < 100 ms |
