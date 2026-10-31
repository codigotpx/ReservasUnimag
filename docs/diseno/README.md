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
