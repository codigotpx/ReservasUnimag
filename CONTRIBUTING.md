# Como trabajamos en ReservasUnimag

Usamos **GitFlow**. Nadie hace push directo a `main` ni a `develop`: todo
cambio entra por Pull Request y necesita la aprobacion de un companero.

## Las ramas

| Rama | Para que sirve | Sale de | Entra a |
|---|---|---|---|
| `main` | Lo que esta entregado. Siempre estable. | — | solo recibe merges |
| `develop` | Lo que llevamos hecho. Es la rama por defecto. | `main` | `main` via release |
| `feature/*` | Una funcionalidad nueva | `develop` | `develop` |
| `fix/*` | Arreglar un bug que no es urgente | `develop` | `develop` |
| `docs/*` | Documentacion y specs | `develop` | `develop` |
| `chore/*` | Configuracion, build, dependencias | `develop` | `develop` |
| `release/x.y.z` | Preparar una entrega | `develop` | `main` **y** `develop` |
| `hotfix/x.y.z` | Arreglo urgente sobre lo entregado | `main` | `main` **y** `develop` |

Los nombres van en minuscula y con guiones:
`feature/registrar-reserva`, `fix/validacion-de-franjas`.

## El dia a dia

```bash
# 1. Partir siempre de develop actualizada
git checkout develop
git pull

# 2. Crear la rama
git checkout -b feature/registrar-reserva

# 3. Trabajar y hacer commits
git add .
git commit -m "Registrar una reserva desde el controlador"

# 4. Antes de subir, verificar que compila
./gradlew build

# 5. Subir y abrir el PR contra develop
git push -u origin feature/registrar-reserva
gh pr create --base develop
```

Cuando el PR tenga los checks en verde y una aprobacion, se mergea y se
borra la rama.

## Si develop avanzo mientras trabajabas

```bash
git checkout develop && git pull
git checkout feature/mi-rama
git merge develop      # resolver conflictos aqui, no en el PR
```

## Preparar una entrega

```bash
git checkout develop && git pull
git checkout -b release/1.0.0
# ajustar la version en build.gradle si hace falta
git push -u origin release/1.0.0

gh pr create --base main      # PR 1: la entrega
gh pr create --base develop   # PR 2: devolver los ajustes a develop
```

**Son dos PR, no uno.** Si solo mergeas a `main`, `develop` se queda sin
esos ajustes y el problema reaparece en la siguiente entrega. Lo mismo
aplica a los `hotfix/*`.

## Reglas que hacen cumplir los bots

- A `main` solo entran `release/*` y `hotfix/*`. Si abres un PR de un
  `feature/*` a `main`, el check **validar** falla.
- `./gradlew build` tiene que pasar. Si un test falla, el check **build**
  falla y no se puede mergear.
- No se puede pushear directo a `main` ni a `develop`, ni forzar push,
  ni borrar esas ramas.

## Commits

Mensajes en espanol, en imperativo, explicando **que** cambia:

```
Validar que la franja no se solape con otra reserva
```

No:

```
cambios
arreglos varios
wip
```

## Antes de subir

```bash
./gradlew build
```

Los tests usan Testcontainers, asi que **necesitas Docker corriendo**:

```bash
sudo systemctl start docker
```
