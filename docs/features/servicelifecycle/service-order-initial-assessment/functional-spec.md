# Especificação Funcional: Registrar triagem inicial da Service Order

| Campo | Valor |
|---|---|
| Feature | `service-order-initial-assessment` |
| Status | Approved |
| Responsável | Matheus Apostulo |
| Atualizado em | 2026-08-22 |
| Aprovado por | Matheus Apostulo |
| Aprovado em | 2026-08-22 |
| Referências | `docs/rfc/RFC-002-service-order-intake-diagnosis-status-plan.md`; feature `service-order-creation` |

> Esta draft propõe confirmar o nome `initialAssessment` e torná-lo obrigatório na criação. A aprovação desta
> especificação confirma essas duas decisões e autoriza uma mudança incompatível para clientes que ainda criam uma
> Service Order sem o campo.

## Problema e resultado esperado

Ao receber um veículo, o Service Advisor precisa registrar o relato inicial e as informações observadas na entrada sem
antecipar uma conclusão técnica. Hoje a Service Order é criada apenas com Customer, Vehicle, snapshot e prioridade, o
que deixa a triagem fora do histórico do atendimento.

Ao final da criação, a Service Order preserva em `initialAssessment` a triagem não técnica informada pelo Service
Advisor e a devolve nas consultas posteriores.

## Atores e cenários

| Ator | Cenário |
|---|---|
| Service Advisor | Cria uma Service Order e registra a triagem inicial do atendimento |
| Technician | Consulta a triagem como contexto para realizar o diagnóstico, sem tratá-la como conclusão técnica |

### Cenário principal

1. O Service Advisor identifica o Customer e o Vehicle e reúne as informações iniciais do atendimento.
2. O Service Advisor cria a Service Order com os dados já exigidos e um `initialAssessment` não vazio.
3. O sistema registra a triagem na Service Order, que permanece com `statusSnapshot` inicial `RECEIVED`.
4. As consultas da Service Order passam a apresentar a triagem registrada.

### Cenário de erro — triagem ausente

1. O Service Advisor tenta criar uma Service Order sem `initialAssessment`, apenas com espaços ou com valor nulo.
2. O sistema rejeita a criação como erro de validação e não persiste uma Service Order parcial.

## Regras de negócio

- O nome canônico do dado é `initialAssessment`; `preDiagnosis` não deve ser usado como sinônimo em código ou
  contrato porque a triagem não é um Diagnosis.
- `initialAssessment` é obrigatório na criação da Service Order e deve conter texto não vazio após desconsiderar
  espaços nas extremidades.
- A triagem registra informações iniciais relatadas pelo Customer ou observadas pelo Service Advisor.
- A triagem não representa serviços diagnosticados, materiais necessários, preços, Estimate ou conclusão técnica.
- O sistema não interpreta a triagem para criar Service Executions nem para inferir um Diagnosis.
- O texto fica associado à Service Order criada e é apresentado em sua resposta detalhada.
- Esta feature não permite alterar `initialAssessment` depois da criação. Uma eventual correção ou histórico de
  versões exige especificação própria.
- A criação continua iniciando a Service Order em `RECEIVED` e sem Service Executions.

## Fora de escopo

- executar ou registrar um Diagnosis;
- criar Service Executions, Estimate, Stock Requirement ou preço com base no texto;
- atualizar, versionar ou auditar alterações da triagem após a criação;
- estruturar sintomas, anexar fotos ou interpretar automaticamente o texto;
- alterar o fluxo explícito de `start-execution`;
- autenticação e autorização do Service Advisor.

## Critérios de aceite

- [x] Criar uma Service Order com os dados atuais e `initialAssessment` válido registra a triagem e retorna a SO com
      `statusSnapshot` inicial `RECEIVED`.
- [x] A consulta detalhada de uma Service Order retorna o mesmo `initialAssessment` registrado na criação.
- [x] O texto da triagem não cria Service Executions e não muda a SO para `IN_DIAGNOSIS`.
- [x] `initialAssessment` ausente, nulo, vazio ou composto apenas por espaços rejeita a requisição sem persistência
      parcial.
- [x] O contrato usa `initialAssessment` e não introduz `preDiagnosis`.
- [x] Clientes que omitem o novo campo recebem erro de validação estável; a incompatibilidade foi aceita
      explicitamente na aprovação desta spec.
