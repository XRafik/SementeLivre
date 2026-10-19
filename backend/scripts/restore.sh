#!/bin/bash
set -e

if [ -z "$1" ]; then
  echo "Uso: ./restore.sh <caminho-do-arquivo.sql.gz>"
  exit 1
fi

ARQUIVO_BACKUP="$1"

if [ ! -f "$ARQUIVO_BACKUP" ]; then
  echo "Arquivo $ARQUIVO_BACKUP não encontrado!"
  exit 1
fi

echo "ATENÇÃO: Este processo irá restaurar os dados no banco de produção!"
read -p "Deseja continuar? (s/N): " CONFIRM
if [[ "$CONFIRM" != "s" && "$CONFIRM" != "S" ]]; then
  echo "Operação cancelada."
  exit 0
fi

echo "Restaurando dados a partir de $ARQUIVO_BACKUP..."
gunzip -c "$ARQUIVO_BACKUP" | docker exec -i -e PGPASSWORD="${DB_PASSWORD:-semente_pass}" semente_livre_db_prod psql -U "${DB_USER:-semente_user}" -d "${DB_NAME:-semente_prod}"

echo "Restauração finalizada com sucesso!"
