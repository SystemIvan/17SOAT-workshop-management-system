# TD 003: Achados de segurança aceitos sem correção no relatório OWASP

**Status:** Accepted  
**Date:** 2026-08-28  
**Reported by:** Leandro Nascimento  
**Affected areas:** `platform` (dependências do `pom.xml`, superfície HTTP), `identity` (conta de bootstrap)  
**Related decisions:** Decisões A, B, D e E de `docs/features/platform/owasp-vulnerability-assessment/functional-spec.md`; AD-016 (`docs/Architecture-Decisions.md`, `Resolved`); `docs/adr/ADR-003-authentication-strategy.md`

---

## Contexto

O relatório de vulnerabilidades exigido pelo desafio (`docs/security/vulnerability-report.md`) foi
produzido em 2026-08-28 com OWASP Dependency-Check 13.0.0 (SCA) e OWASP ZAP (DAST), sobre o projeto
inteiro. A regra acordada com o coordenador é corrigir obrigatoriamente as vulnerabilidades **Críticas e
Altas**; achados que sejam falso positivo ou que não possam ser corrigidos agora precisam ser registrados
com justificativa, risco e ação de mitigação.

A frente SCA encontrou 96 achados no baseline (run de CI `33220097929`). A atualização do
`tomcat-embed-core` de 11.0.22 para 11.0.25 eliminou 8 Críticas e 6 Altas, confirmado por rescan (run
`33220563271`): o retrato final é **0 Crítica, 5 Altas, 72 Médias**. A frente DAST (run `33221745305`)
não encontrou nenhuma vulnerabilidade Crítica, Alta ou Média, com 117 regras de active scan aprovadas.

Este documento é o registro único e agregado de tudo que **não** foi corrigido, conforme a Decisão E
(opção E1), escolhida pelo responsável para não fragmentar `docs/tech-debt/` em dezenas de arquivos. Ele
refina o §5 do `rascunho/plano-owasp-vulnerability-assessment.md`, que previa um registro por achado: o
conteúdo exigido por achado é preservado linha a linha na tabela, muda apenas a granularidade de
arquivos.

## A dívida

Cinco vulnerabilidades de severidade **Alta** permanecem em produção sem correção disponível, e 73
achados de severidade Média/Baixa permanecem conhecidos e não tratados. Nenhum deles bloqueia o
entregável segundo a regra acordada, mas todos representam risco real que o projeto está assumindo
deliberadamente em vez de por omissão.

O ponto central da dívida não é a existência dos achados — é que **o projeto não tem hoje nenhum
mecanismo que force sua reavaliação**. O passo de SCA permanente no CI foi configurado para apenas
relatar, nunca reprovar (Decisão C, opção C2), justamente para não bloquear PRs alheias a segurança. Sem
uma rotina de revisão, esta lista envelhece silenciosamente.

## Evidência

### Grupo 1 — `mysql-connector-j` 9.7.0: 5 Altas sem correção publicada

| CVE | CVSS | Justificativa | Risco | Mitigação |
|---|---|---|---|---|
| CVE-2026-60193 | 8.5 | Não existe release corrigida | Comprometimento via servidor MySQL malicioso ou resposta forjada | Banco não exposto à internet; acesso só pela rede interna do compose |
| CVE-2026-60192 | 8.1 | Idem | Idem | Idem |
| CVE-2026-60586 | 7.7 | Idem | Idem | Idem |
| CVE-2026-60317 | 7.4 | Idem | Idem | Idem |
| CVE-2026-60623 | 7.1 | Idem | Idem | Idem |

Os cinco CVEs declaram como afetadas as versões **9.7.0 e 9.7.1**, e o Maven Central publica no máximo
9.7.0 — não existe versão para onde subir. Enquadra-se na opção **A3** da Decisão A (aguardar release
upstream).

Um downgrade para 9.6.0 escaparia desse intervalo específico, mas foi **descartado**: trocaria um risco
medido por um risco não medido (versões anteriores não foram escaneadas) e regrediria o driver JDBC de
toda a aplicação, contrariando a restrição de impacto mínimo sob a qual a feature foi executada.

### Grupo 2 — 72 achados Médios de SCA

| Origem | Achados | Justificativa | Risco | Mitigação |
|---|---|---|---|---|
| `swagger-ui` 5.32.2, via `springdoc` 3.0.3 | ~28 | Severidade Média está fora do escopo de correção obrigatória | XSS e poluição de protótipo contra quem abrir a Swagger UI; não afeta os endpoints da API | Avaliar bump do `springdoc`; considerar desabilitar a Swagger UI fora do perfil `dev` |
| `wiremock-standalone` 3.13.1 (escopo `test`) | ~40 | Escopo `test`: não entra no artefato de produção (Decisão B, opção B3) | Restrito à máquina de build/CI | Excluído do passo permanente de CI; nenhuma exposição em runtime |
| `log4j-api` 2.25.4 | 1 | Severidade Média | Baixo no uso atual | Corrigível por bump do parent para Spring Boot 4.1.1 (traz 2.25.5) |
| `jackson-databind` 2.21.4 | 1 | Severidade Média, dependência transitiva de teste | Restrito ao build | Reavaliar junto do bump do `wiremock` |
| `mysql-connector-j` 9.7.0 | 2 | Sem release corrigida, como o Grupo 1 | Idem Grupo 1 | Idem Grupo 1 |

**Nota sobre contagem:** o número 72 é inflado pela forma como o Dependency-Check conta. Ele reporta um
achado por par (arquivo, CVE), e tanto `springdoc` quanto `wiremock` embutem cópias do `swagger-ui` em
dois arquivos cada (`swagger-ui-bundle.js` e `swagger-ui-es-bundle.js`). O mesmo CVE chega a ser contado
quatro vezes. O número de CVEs distintos é da ordem de **29**, não 72.

### Grupo 3 — 1 achado Baixo de DAST

| Achado | Instâncias | Justificativa | Risco | Mitigação |
|---|---|---|---|---|
| Cross-Origin-Resource-Policy header ausente ou inválido (ZAP 90004) | 5 | Severidade Baixa, fora do escopo de correção obrigatória | Recursos podem ser embutidos por origem cruzada; impacto baixo numa API JSON autenticada | Definir `Cross-Origin-Resource-Policy: same-origin` em `SecurityConfig` — mudança pequena, mas altera código de produção em funcionamento |

Este é o único achado da frente DAST. As outras 195 instâncias são informativas (respostas 4xx esperadas
do fuzzing, conteúdo não armazenável, identificação do endpoint de login) e não constituem vulnerabilidade.

### Grupo 4 — Achados herdados de features anteriores

| Achado | Justificativa | Risco | Mitigação |
|---|---|---|---|
| Senha de bootstrap `admin`/`changeme123` fixada na migration `V20260824120001__seed_bootstrap_admin_account.sql` | Corrigir exigiria alterar migration já incorporada à baseline operacional, o que `AGENTS.md` §"Persistent data and seeds" trata como imutável (Decisão D, opção D1) | Acesso administrativo total a quem conhecer a credencial pública do repositório | Rotação obrigatória em qualquer ambiente não demonstrativo, documentada no `README.md`; alternativa futura é migration aditiva com rotação forçada no primeiro login |
| Contas de demonstração `manager.dev`, `technician.dev`, `customer.dev`, todas com `changeme123` | Mesmo raciocínio; criadas por seeder | Idem, com papéis menores | Só existem sob perfil `dev` **e** `app.seed.enabled=true` |
| Ausência de análise estática (SAST) | Nunca configurada; item deixado explicitamente aberto no Approval Checklist da `ADR-003` | Defeitos de segurança em código próprio não são detectados automaticamente | Declarado fora de escopo desta feature; exige trabalho próprio de integração |

Os dois primeiros já haviam sido aceitos com mitigação em
`docs/features/platform/jwt-authentication/implementation-plan.md`, com a observação de que deveriam
constar do relatório de vulnerabilidades do desafio. Este documento cumpre esse rastreamento, que antes
apontava para um `GAPS-TECH-CHALLENGE.md` §6 que nunca chegou a ser commitado no repositório.

## Impacto se não for pago

- As 5 Altas do `mysql-connector-j` permanecem exploráveis enquanto a Oracle não publicar correção. Como
  o passo de CI apenas relata e nunca reprova, **nada avisa o time quando a correção finalmente sair** —
  o risco é ficar desatualizado por inércia, não por decisão.
- Os CVEs do `swagger-ui` são os únicos do Grupo 2 que alcançam produção. Quanto mais tempo a Swagger UI
  ficar publicamente acessível com a versão atual, maior a janela de exposição.
- A senha de bootstrap conhecida é o achado de maior impacto potencial do conjunto: é acesso `ADMIN`
  completo, e a mitigação depende inteiramente de disciplina humana de rotação, sem nenhuma verificação
  automática de que aconteceu.
- Sem SAST, cada feature nova depende exclusivamente do checklist manual do `AGENTS.md` para detectar
  problemas de segurança em código próprio.

## Opções de encaminhamento

### Opção A: Reavaliação periódica dos achados aceitos

- Executar o workflow `security-scan.yml` em cadência definida (por exemplo, mensal ou a cada release) e
  revisar esta tabela, promovendo a correção assim que uma versão corrigida do `mysql-connector-j` for
  publicada.
- **Esforço:** baixo por execução; exige combinar a cadência e um responsável.
- **Prós:** ataca a causa real da dívida, que é a ausência de gatilho de reavaliação.
- **Contras:** depende de disciplina de processo; sem dono nomeado, degrada.

### Opção B: Reduzir o Grupo 2 antes da entrega

- Bump do `springdoc` (se houver versão que empacote `swagger-ui` corrigido) e do `wiremock`, mais bump
  do parent para Spring Boot 4.1.1 (resolve o `log4j-api`).
- **Esforço:** estimado em 1,5 a 3 horas, incluindo `./mvnw clean verify` após cada mudança.
- **Prós:** reduziria o total de 77 para algo entre 10 e 20 achados.
- **Contras:** ganho concentrado em severidade Média, que a regra do coordenador não exige corrigir; o
  ganho do `springdoc` não foi verificado e pode ser zero se a versão nova embutir o mesmo `swagger-ui`.
- **Atenção:** o Spring Boot 4.1.1 gerencia Tomcat 11.0.24, *abaixo* do 11.0.25 aplicado nesta feature. O
  override explícito de `tomcat.version` no `pom.xml` prevalece, mas isso precisa ser confirmado com
  `./mvnw dependency:list` após o bump, não assumido.

### Opção C: Endurecer o gate de CI

- Reverter a Decisão C2 e passar a reprovar o build a partir de CVSS 7.0.
- **Esforço:** trivial (uma linha no `pom.xml`).
- **Prós:** impede que novas Críticas/Altas entrem despercebidas.
- **Contras:** hoje reprovaria imediatamente por causa das 5 Altas do `mysql-connector-j`, que não têm
  correção — exigiria suprimi-las primeiro, o que reintroduz o risco de esconder achado por conveniência.

## Recomendação

**Opção A** como encaminhamento imediato, combinada na revisão da PR desta feature. A dívida real não é a
lista de achados, que está medida e justificada, mas a ausência de um gatilho de reavaliação: o passo de
CI foi deliberadamente configurado para não reprovar, então nada além de decisão humana trará estes itens
de volta à discussão.

A **Opção B** é opcional e de bom custo-benefício se houver tempo antes da entrega, mas nenhum de seus
itens é exigido pela regra do coordenador.

A **Opção C** não deve ser adotada enquanto o `mysql-connector-j` não tiver correção, porque hoje só
seria viável junto de uma supressão — e suprimir um achado Alto real para destravar o build é exatamente
a prática que o `owasp-suppressions.xml` deste projeto proíbe.

Qualquer mudança que altere a política de reprovação do CI, o escopo do scan ou o tratamento da senha de
bootstrap é decisão de time, não de contribuidor individual, pelo mesmo critério usado em
`docs/Architecture-Decisions.md`.

## Custo de não decidir agora

O trabalho pode continuar sem resolver esta dívida: nenhum caso de uso está bloqueado e o entregável do
desafio está satisfeito, com 0 vulnerabilidades Críticas e as 5 Altas restantes documentadas por
impossibilidade técnica de correção.

Suposições temporárias a respeitar enquanto a dívida não for paga:

- Não tratar a ausência de reprovação no CI como sinal de que o projeto está livre de Críticas/Altas —
  ele apenas não bloqueia.
- Não adicionar supressões ao `owasp-suppressions.xml` para achados reais, apenas para falsos positivos
  com justificativa escrita.
- Não considerar a conta `ADMIN` de bootstrap segura em nenhum ambiente que não seja demonstração local.

---

**Last Updated:** 2026-08-28  
**Status:** Accepted — dívida assumida deliberadamente; ver Recomendação para o encaminhamento proposto
