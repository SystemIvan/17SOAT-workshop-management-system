#!/bin/bash
# wait-for-mysql.sh - Aguarda MySQL estar pronto antes de iniciar a aplicação
# Implementado para garantir startup automático confiável em docker compose

set -e

# Parâmetros com defaults
MYSQL_HOST="${SPRING_DATASOURCE_URL##*://}"
MYSQL_HOST="${MYSQL_HOST%%:*}"
MYSQL_PORT="${SPRING_DATASOURCE_URL##*:}"
MYSQL_PORT="${MYSQL_PORT%%/*}"
MYSQL_USER="${SPRING_DATASOURCE_USERNAME:-workshop_user}"
MYSQL_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-workshop_pass}"

# Defaults se não conseguir parsear
: "${MYSQL_HOST:=mysql}"
: "${MYSQL_PORT:=3306}"
: "${MAX_RETRIES:=30}"
: "${RETRY_INTERVAL:=2}"

echo "⏳ Aguardando MySQL em $MYSQL_HOST:$MYSQL_PORT..."

retry_count=0
while [ $retry_count -lt $MAX_RETRIES ]; do
  if nc -z "$MYSQL_HOST" "$MYSQL_PORT" 2>/dev/null; then
    echo "✓ MySQL está acessível"

    # Tentar conexão real com mysql client
    if mysql -h "$MYSQL_HOST" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT 1" >/dev/null 2>&1; then
      echo "✓ MySQL autenticação bem-sucedida - iniciando aplicação"
      break
    fi
  fi

  retry_count=$((retry_count + 1))
  if [ $retry_count -lt $MAX_RETRIES ]; then
    echo "  Tentativa $retry_count/$MAX_RETRIES - aguardando ${RETRY_INTERVAL}s..."
    sleep $RETRY_INTERVAL
  fi
done

if [ $retry_count -eq $MAX_RETRIES ]; then
  echo "✗ Timeout aguardando MySQL após $(($MAX_RETRIES * $RETRY_INTERVAL))s"
  exit 1
fi

# Executar comando passado como argumento (a aplicação)
echo "🚀 Iniciando aplicação..."
exec "$@"
