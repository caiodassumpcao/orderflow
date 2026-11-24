# Contribuindo com o OrderFlow

## Ambiente

Pré-requisitos: Docker 24+ com Compose v2, Java 21, Node 24 LTS e GNU Make.

```bash
cp .env.example .env              # ajuste as senhas locais
git config core.hooksPath .githooks
make up                           # infra local
make backend-run                  # API (perfil dev)
make frontend-run                 # SPA
```

## Fluxo de branches (Git Flow)

| Branch | Uso |
|---|---|
| `main` | Produção; só recebe merges de `release/*` e `hotfix/*` |
| `develop` | Integração |
| `feature/<descricao>` | Novas funcionalidades, a partir de `develop` |
| `release/x.y.z` | Estabilização de versão |
| `hotfix/<descricao>` | Correções urgentes a partir de `main` |

Todo trabalho entra via **Pull Request** para `develop`, com o template preenchido e CI verde. `main` e `develop` são protegidas (PR obrigatório, CI obrigatório, sem force-push).

## Commits

Use [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`, `ci:`, `build:`, `perf:`. Escopo opcional (`feat(orders): ...`). Commits pequenos e atômicos.

O hook `.githooks/commit-msg` rejeita mensagens com `Co-Authored-By`, `Generated with` ou referências a assistentes de IA. Teste do hook: `sh .githooks/tests/commit-msg.test.sh`.

## Antes de abrir o PR

```bash
make format
make lint
make test
make test-e2e      # quando houver mudança de UI
```

Checklist completo: ver Definition of Done no `README.md`.

## Código

- Backend: arquitetura hexagonal por contexto, regras verificadas por ArchUnit. Formatação google-java-format (Spotless) e Checkstyle.
- Frontend: Angular standalone, TypeScript strict, ESLint + Prettier, acessibilidade WCAG 2.2 AA.
- Decisões arquiteturais relevantes viram ADR em `docs/adr/` (formato MADR).
