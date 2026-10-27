#!/bin/bash
set -e

BACKUP_DIR="./backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
FILENAME="$BACKUP_DIR/backup_semente_prod_$TIMESTAMP.sql.gz"

mkdir -p "$BACKUP_DIR"

echo "Iniciando backup do banco de dados de produção..."
docker exec -e PGPASSWORD="${DB_PASSWORD:-semente_pass}" semente_livre_db_prod pg_dump -U "${DB_USER:-semente_user}" -d "${DB_NAME:-semente_prod}" | gzip > "$FILENAME"

echo "Backup concluído com sucesso: $FILENAME"
