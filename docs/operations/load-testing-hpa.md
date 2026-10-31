# Teste de carga e escala automática (HPA)

Como gerar carga na API com [k6](https://k6.io) e observar o Horizontal Pod Autoscaler (HPA) aumentar e reduzir o
número de pods. Pré-requisito: o [ambiente Kubernetes local](../setup/local-kubernetes.md) no ar, com a readiness em
`UP` e o HPA em `REPLICAS 1`.

## Como o HPA decide

| Parâmetro | Valor | Onde |
| --- | --- | --- |
| Métrica | Uso de CPU em relação ao *request* do container (250m) | `k8s/base/app-hpa.yaml` |
| Alvo | 70% (média entre os pods) | idem |
| Mínimo e máximo de pods | 1 e 3 | idem |
| Janela de estabilização para reduzir | 60 s | idem |
| Fonte das métricas | metrics-server (coleta a cada ~15 s) | `infra/local/cluster` |

A cada ciclo, o HPA calcula `pods desejados = ceil(pods atuais × uso atual / alvo)`. Exemplo: 1 pod a 154% de CPU →
`ceil(1 × 154 / 70) = 3`, limitado ao máximo de 3. Para reduzir, ele espera a janela de 60 s com o uso baixo, o que
evita derrubar pods num vale passageiro da carga.

A escala é só por CPU: a JVM não devolve o heap depois de um pico, então um alvo de memória manteria pods ociosos de pé.
Os detalhes estão no [glossário](../learning/glossario.md#hpa-por-memória-com-jvm).

## O script de carga

`load-test/hpa-load.js`, executado pela imagem `grafana/k6:1.3.0` (o k6 não precisa ser instalado).

| Fase | Duração | Usuários virtuais |
| --- | --- | --- |
| Subida | 1 min | 0 → 10 |
| Sustentação | 3 min | 10 → 20 |
| Descida | 1 min | 20 → 0 |

- Cada usuário virtual repete, a cada 0,5 s: **80%** das vezes `GET /api/service-orders` com o token, **20%** das vezes
  um login. O login usa BCrypt, que consome CPU de forma previsível; é ele que move a métrica do HPA.
- O token é obtido uma vez (`setup`) com o admin de bootstrap e compartilhado por todos os usuários virtuais.
- Limiares de aprovação: menos de 1% de falhas e p95 abaixo de 2 s. Se um deles não for atingido, o k6 termina com
  erro.

Variáveis aceitas: `BASE_URL` (padrão `http://host.docker.internal:8080`), `ADMIN_USERNAME` e `ADMIN_PASSWORD`.

## Passo a passo

Use três terminais lado a lado.

**Terminal 1: a decisão do HPA**

```bash
kubectl get hpa -n workshop -w
```

**Terminal 2: os pods**

```bash
kubectl get pods -n workshop -l app.kubernetes.io/name=workshop-app -w
```

**Terminal 3: a carga**

```bash
make load-test
```

Sem make (PowerShell):

```powershell
docker run --rm --add-host=host.docker.internal:host-gateway `
    -v "${PWD}/load-test:/scripts:ro" -e BASE_URL=http://host.docker.internal:8080 `
    grafana/k6:1.3.0 run /scripts/hpa-load.js
```

Dentro do container do k6, `localhost` é o próprio container. Por isso o endereço é `host.docker.internal`, que aponta
para a máquina host; o `--add-host` garante esse nome também no Linux.

## O que esperar

Resultado medido em 2026-10-18 e repetido em 2026-10-27 (Docker Desktop com 7 GB e 5 CPUs para o WSL2):

| Momento | Terminal 1 (HPA) | Terminal 2 (pods) |
| --- | --- | --- |
| Antes da carga | `cpu: ~2%/70%`, 1 réplica | 1 pod `1/1` |
| ~1 min de carga | CPU passa de 100% → 2 réplicas | pod novo `0/1` → `1/1` em ~30 s |
| Pico | 124% a 167% → 3 réplicas | 3 pods `1/1` |
| ~1 min após o fim | CPU baixa → 3 → 2 → 1 | pods extras em `Terminating` e depois `Error` |

O `Error` no fim não é falha: para remover um pod, o Kubernetes envia o sinal SIGTERM, o Spring encerra a aplicação e
a JVM sai com código diferente de zero (em geral 143 = 128 + 15, o número do SIGTERM). O `kubectl` mostra `Error`
para qualquer saída diferente de zero, mesmo quando foi ele quem pediu o encerramento. `kubectl get events -n
workshop` mostra `Killing ... Stopping container app` e `SuccessfulRescale ... All metrics below target`.

Resumo do k6 nessas execuções: 6727 e 6708 requisições, **0 falhas**, p95 de 77 ms nas duas. O endereço não mudou
durante a escala: o Service distribuiu as requisições entre os pods prontos sem nenhuma configuração no cliente.

Um pod novo nasce com CPU alta (~1000m) por causa do boot da JVM. Isso não provoca escala em cascata: o HPA ignora a
CPU de pods que ainda não estão prontos.

## Problemas comuns

| Sintoma | Causa provável | O que fazer |
| --- | --- | --- |
| `Login failed ... is the API reachable` no início | O k6 não alcança a API | Conferir `curl http://localhost:8080/actuator/health/readiness` no host (no PowerShell, `curl.exe`); no Linux, manter o `--add-host` |
| HPA em `<unknown>/70%` | metrics-server ainda sem dados do pod | Aguardar ~1 min; conferir `kubectl top pods -n workshop` |
| Não passa de 1 réplica | Pouca carga para a máquina | Aumentar o alvo de usuários virtuais em `stages` |
| Não volta a 1 réplica | Carga residual ou janela de 60 s ainda correndo | Aguardar ~2 min sem carga; `kubectl describe hpa workshop-app -n workshop` mostra o motivo |
| Requisições falhando durante a escala | Pod recebendo tráfego antes de pronto | Não deve ocorrer: a readiness só libera o pod quando o banco responde. Investigar com `kubectl describe pod` |
