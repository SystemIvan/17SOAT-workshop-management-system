# Verificação de saúde

A API expõe dois endpoints públicos (sem token, somente `GET`) do Spring Boot Actuator, usados pelos probes do
Kubernetes e pelo healthcheck do `docker-compose.yml`. A resposta contém só o status agregado, sem componentes nem
detalhes; nenhum outro endpoint do Actuator é exposto.

| Endpoint | Pergunta que responde | Depende do banco | Sucesso | Falha |
| --- | --- | --- | --- | --- |
| `GET /actuator/health/liveness` | O processo está vivo? (falha → reiniciar o container) | Não | `200 {"status":"UP"}` | `503` |
| `GET /actuator/health/readiness` | Pode receber tráfego? (falha → sair do balanceamento) | Sim | `200 {"status":"UP"}` | `503 {"status":"DOWN"}` |

```bash
curl -i http://localhost:8080/actuator/health/liveness
curl -i http://localhost:8080/actuator/health/readiness
```

Para ver a diferença entre os dois, pare o banco: a readiness passa a `503` e a liveness continua `200` (a aplicação
não é reiniciada, apenas deixa de receber tráfego). Ao religar o banco, a readiness volta a `200`.

```bash
docker compose stop mysql
curl -i http://localhost:8080/actuator/health/readiness   # 503
curl -i http://localhost:8080/actuator/health/liveness    # 200
docker compose start mysql
```

Na coleção Postman, a pasta `Health` contém `Liveness probe` e `Readiness probe`; ambas são os primeiros passos do
`make e2e`.
