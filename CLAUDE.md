# CLAUDE.md — contexto e regras para agentes

Este arquivo orienta agentes de código (e humanos) que trabalham neste repositório. O `README.md` é a fonte da verdade sobre o produto; aqui ficam as regras operacionais.

## Visão do projeto

**OrderFlow** é uma plataforma de gestão de pedidos **event-driven** com **CQRS**. Projeto de portfólio que demonstra engenharia full stack: arquitetura limpa, mensageria confiável (outbox, idempotência, retry/DLQ), SQL otimizado, testes automatizados, acessibilidade (WCAG 2.2 AA), segurança e DevOps.

Stack: Java 21 + Spring Boot 4.x (Maven) · Angular 22 standalone + TypeScript strict · PostgreSQL 16 · Kafka (KRaft) · Keycloak (OIDC/JWT) · Docker / GitHub Actions.

O trabalho é dividido em fases (ver Roadmap no README). **Implemente somente a fase em andamento.**

## Arquitetura

- **Monólito modular** com dois bounded contexts: `orders` e `inventory`, comunicando-se por eventos Kafka (ver `docs/adr/0001-modular-monolith.md`).
- **Hexagonal por contexto**, pacote base `com.caiodassumpcao.orderflow`:
  - `<contexto>.domain` — modelo puro. **Proibido** depender de Spring, JPA/Jakarta Persistence ou Hibernate.
  - `<contexto>.application` — casos de uso; depende só de `domain`.
  - `<contexto>.adapter.{web,persistence,messaging}` — integrações; dependem de `application`/`domain`.
  - `shared.{config,security,messaging,error,observability}` — infraestrutura transversal.
- Regras impostas por **ArchUnit** (`backend/src/test/java/.../architecture`). Não enfraqueça regras para fazer o build passar.
- **CQRS:** escrita no schema `command`, leitura no schema `query` (PostgreSQL). Migrations Flyway em `backend/src/main/resources/db/migration`, **imutáveis** depois de mergeadas.
- **Frontend:** `src/app/{core,shared,features}`; features com rotas lazy (`loadComponent`/`loadChildren`).

## Convenções de código

- **Java:** formatação google-java-format via Spotless; Checkstyle em `backend/config/checkstyle/checkstyle.xml`. Sem imports `*`. Records para DTOs/eventos imutáveis. Injeção por construtor.
- **Erros HTTP:** sempre RFC 9457 (`application/problem+json`).
- **Logs:** JSON estruturado; nunca logar segredos, tokens ou PII. Use o `correlationId` do MDC.
- **Config:** tudo via variáveis de ambiente; sem credenciais default em `application.yml`.
- **TypeScript/Angular:** `strict`, standalone components, signals, `OnPush`. ESLint + Prettier. Templates acessíveis (landmarks, labels, foco visível).
- **Testes:** nome descreve o comportamento; Testcontainers para integração (sufixo `IT`), sem mocks de banco.

## Comandos

| Comando | O que faz |
|---|---|
| `make help` | Lista os alvos |
| `make up` / `make down` | Sobe/derruba a infra (Postgres, Kafka, Kafka UI, Keycloak) |
| `make backend-run` | API em `http://localhost:8080` (perfil `dev`) |
| `make frontend-run` | SPA em `http://localhost:4200` |
| `make test` | Testes backend (unit + integração com Testcontainers) e frontend (Jest) |
| `make test-e2e` | Playwright + axe |
| `make lint` / `make format` | Verifica / aplica formatação e lint |
| `make clean` | Remove artefatos de build |

Pré-requisitos: Docker, Java 21, Node 24, GNU Make. Copie `.env.example` para `.env` antes do `make up`.

## Regras de Git

- Fluxo: `main` ← `develop` ← `feature/*` (também `release/*`, `hotfix/*`). Todo trabalho entra por PR para `develop`.
- **Conventional Commits** (`feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `ci`, `build`, `perf`), commits pequenos e atômicos.
- **NUNCA** adicione `Co-Authored-By`, "Generated with", nem qualquer referência ao Claude/Anthropic em commits, PRs, issues ou arquivos do projeto. O hook `.githooks/commit-msg` bloqueia isso (`git config core.hooksPath .githooks`).
- Nunca faça `push`, `force-push`, rebase de branches publicadas ou reescrita de histórico sem pedido explícito.
- Nunca use `--no-verify`.

## Definition of Done

- Critérios de aceite atendidos; mudança revisada via PR.
- `make lint` e `make test` verdes; cobertura mantida (domínio/aplicação ≥ 80% linhas).
- ArchUnit sem violações.
- Sem vulnerabilidades altas/críticas nos scans (CodeQL, Trivy, Dependency-Check, gitleaks).
- Acessibilidade verificada quando houver UI (axe sem violações).
- README/ADR/docs atualizados; nenhum TODO sem issue ou nota no README.
- CI verde.

## O que NÃO fazer

- Não versionar segredos (`.env`, chaves, tokens). Senhas de dev só via `.env`.
- Não antecipar fases futuras (domínio, producers Kafka, telas) fora do escopo pedido.
- Não desligar testes, lint, ArchUnit, scans ou checks de cobertura para "fazer passar"; corrija a causa.
- Não adicionar dependências sem necessidade clara; prefira o que o Spring Boot já gerencia.
- Não editar migrations Flyway já aplicadas; crie uma nova versão.
- Não rodar containers como root nem usar imagens sem tag fixa.
- Não declarar que algo funciona sem ter executado build/testes.
