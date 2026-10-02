#!/usr/bin/env bash
set -euo pipefail
command -v jq >/dev/null || { echo 'Instale jq para executar a demonstração.' >&2; exit 1; }
api_url=${API_URL:-http://localhost:8080}
marker=$(cat /proc/sys/kernel/random/uuid)
request_key=$(cat /proc/sys/kernel/random/uuid)
password=$(openssl rand -hex 24)
email="demo-${marker}@example.com"
curl -fsS "$api_url/api/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Synthetic Owner\",\"email\":\"$email\",\"password\":\"$password\"}" >/dev/null
token=$(curl -fsS "$api_url/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"password\":\"$password\"}" | jq -er .accessToken)
customer=$(curl -fsS "$api_url/api/customers" -H "Authorization: Bearer $token" \
  -H "Idempotency-Key: $request_key" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Synthetic Customer\",\"email\":\"customer-${marker}@example.com\"}")
id=$(jq -er .id <<<"$customer")
for attempt in 1 2; do
  curl -fsS "$api_url/api/customers/$id" -H "Authorization: Bearer $token" >/dev/null
done
curl -fsS -X PUT "$api_url/api/customers/$id" -H "Authorization: Bearer $token" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Updated Customer\",\"email\":\"updated-${marker}@example.com\",\"revision\":1}" >/dev/null
curl -fsS -X DELETE "$api_url/api/customers/$id?revision=2" -H "Authorization: Bearer $token" >/dev/null
for attempt in $(seq 1 60); do
  notifications=$(curl -fsS "$api_url/api/notifications" -H "Authorization: Bearer $token")
  count=$(jq --arg id "$id" '[.[] | select(.customerId == $id)] | length' <<<"$notifications")
  if [ "$count" -eq 3 ]; then
    echo "Fluxo completo concluído para Customer $id: criação, atualização, remoção e três notificações."
    jq --arg id "$id" '[.[] | select(.customerId == $id) | {eventId,revision,type}]' <<<"$notifications"
    exit 0
  fi
  sleep 1
done
echo "Tempo esgotado esperando notificações de Customer $id. Verifique Outbox, Kafka e DLT." >&2
exit 1
