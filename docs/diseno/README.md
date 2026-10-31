# Diseño

Referencia visual del frontend, exportada de Figma.

- `pantallas/`: una captura PNG por pantalla, con el nombre de la ruta que implementa, por ejemplo `login.png`, `resources.png` o `my-reservations.png`. Si una pantalla tiene varios estados, se agrega el estado al final: `resources-vacio.png`, `resources-error-inventario.png`.

Los assets que sí usa la aplicación (íconos, logos, imágenes, tipografías) no van aquí, sino en `frontend/public/`.

## Pantallas

| Captura | Ruta | Momento |
|---|---|---|
| `intro-bienvenida.png` | `/login` | Introducción, paso 1 |
| `intro-escudo.png` | `/login` | Introducción, paso 2 |
| `login.png` | `/login` | Formulario |
| `consultar-recursos-filtros.png` | `/resources` | Formulario de búsqueda |
| `consultar-recursos-cargando.png` | `/resources` | Esperando la respuesta |
| `consultar-recursos-espacios.png` | `/resources` | Resultados de espacios |
| `consultar-recursos-activos.png` | `/resources` | Resultados de activos |
| `reservar-espacio.png` | `/resources` | Hoja para confirmar la reserva de un espacio |
| `sheet-prestar-activo.png` | `/resources` | Hoja para confirmar el préstamo de un activo |
| `error-bloqueo-docente.png` | `/resources` | Denegación `RES-001` |
| `error-limite-alcanzado.png` | `/resources` | Denegación `RES-002` |
| `error-sancion-activa.png` | `/resources` | Denegación `RES-003` |
| `error-recurso-tomado.png` | `/resources` | Denegación `RES-004` |
| `mis-reservas-vigentes.png` | `/my-reservations` | Reservas vigentes y cupo |
| `mis-reservas-historial.png` | `/my-reservations?tab=historial` | Historial por mes |
| `reservas-cancelar.png` | `/my-reservations` | Hoja para confirmar la cancelación |
| `admin-importar-horarios.png` | `/schedules` | Dirección: carga válida que desplaza reservas |
| `admin-importar-horarios-rechazada.png` | `/schedules` | Dirección: carga con filas rechazadas |
| `admin-formato-archivo.png` | `/schedules/format` | Dirección: cómo debe ser el CSV |
