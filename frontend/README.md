# Frontend

Next.js (App Router) con React, TypeScript, Tailwind y los íconos de lucide-react. La estructura sigue el [plan de arquitectura](../docs/plan/plan-arquitectura.md#frontend).

## Arrancar

```bash
npm install
npm run dev        # http://localhost:3000
```

El backend tiene que estar corriendo. Todo lo que pida `/api/*` lo reenvía Next.js a `BACKEND_URL`, que por defecto es `http://localhost:8080`.

## Carpetas

```text
frontend/
├── public/
│   ├── icons/                         íconos exportados de Figma (SVG)
│   ├── images/                        logos e imágenes, en WebP
│   └── fonts/                         tipografías propias, si el diseño las usa
└── src/
    ├── app/                           rutas (App Router)
    │   ├── (auth)/login/, register/
    │   ├── (app)/resources/           UC1, UC8 y reservar (UC2); la usa también Dirección
    │   ├── (app)/profile/             datos de la sesión y cerrar sesión
    │   ├── (student)/my-reservations/ ver y cancelar (UC4)
    │   └── (direction)/schedules/     importar horarios (UC3)
    ├── components/                    componentes visuales reutilizables
    ├── features/                      lógica por funcionalidad: resources, reservations,
    │                                  schedules, auth (hooks, formularios, tipos, api.ts)
    └── lib/api/                       cliente HTTP hacia /api del backend
```

Las capturas de las pantallas de Figma, que sirven de referencia y no se publican, van en [`docs/diseno/pantallas/`](../docs/diseno/).

El frontend no tiene reglas de negocio: valida formularios por usabilidad, pero la decisión final la toma siempre el backend.
