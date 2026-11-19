#!/usr/bin/env bash
# Despliegue en la VM. Lo corre el workflow .github/workflows/deploy.yml por SSH en cada push a main,
# y también se puede correr a mano dentro de la VM.
#
# La clave de despliegue de GitHub está atada en ~/.ssh/authorized_keys a un solo comando, que primero deja el
# repo en origin/main y después ejecuta este script:
#   command="cd ~/ReservasUnimag && git fetch -q origin main && git checkout -q -B main origin/main && exec ./deploy/deploy.sh",restrict ssh-ed25519 ...
# Esa parte vive fuera del repo para que el script que se ejecuta sea siempre el de la versión recién traída.
set -euo pipefail

cd "$(dirname "$0")/.."
compose=(docker compose -f docker-compose.prod.yml)

echo "Desplegando $(git log --oneline -1)"

# Recompila la imagen del backend y recrea solo lo que cambió. PostgreSQL, Kafka y sus datos se quedan como estaban
"${compose[@]}" up -d --build --remove-orphans

# Las imágenes viejas del backend se acumulan en cada despliegue y el disco de la VM es de 30 GB
docker image prune -f > /dev/null

"${compose[@]}" ps --format '{{.Service}}: {{.Status}}'
