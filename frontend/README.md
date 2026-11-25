# OrderFlow — frontend

SPA Angular 22 (standalone, signals, `OnPush`, zoneless) com Angular Material (M3, temas claro e escuro) e login OIDC com PKCE no Keycloak. Visão geral e roadmap no [README da raiz](../README.md).

| Comando                           | O que faz                                                 |
| --------------------------------- | --------------------------------------------------------- |
| `npm start`                       | Dev server em `http://localhost:4200`                     |
| `npm run build`                   | Build de produção em `dist/`                              |
| `npm test` / `npm run test:ci`    | Testes unitários (Jest + Testing Library) / com cobertura |
| `npm run lint` / `npm run format` | ESLint + Prettier: verifica / corrige                     |
| `npm run e2e`                     | Playwright + axe (sobe o dev server sozinho)              |

## Estrutura

- `src/app/core`: infraestrutura do app.
  - `config`: `/config.json` lido antes do bootstrap (URL da API e emissor OIDC).
  - `auth`: configuração OIDC, estado de login em signals e guard de papel.
  - `api`: clientes HTTP e contratos da API.
  - `http`: Problem Details (RFC 9457) traduzidos em mensagens.
- `src/app/shared`: componentes e utilitários reutilizáveis.
  - Componentes: selo de status, detalhes do pedido, alerta de erro, diálogo de confirmação.
  - Utilitários: paginação por cursor e acompanhamento do pedido durante a saga.
- `src/app/features`: telas com rotas lazy.
  - `home` e `forbidden` (acesso negado).
  - `orders` (cliente): rotas `/pedidos`, `/pedidos/novo` e `/pedidos/:id`.
  - `admin` (operação): rotas `/admin`, `/admin/pedidos` e `/admin/estoque`.
- `src/testing`: helpers dos testes unitários (fora da cobertura).

## Configuração de runtime

A SPA não tem URLs no build. Em dev, `public/config.json` aponta para a API em `:8080` e o Keycloak em `:8180`. Na imagem, o nginx gera `/config.json` e a CSP das variáveis `ORDERFLOW_API_URL`, `ORDERFLOW_OIDC_AUTHORITY` e `ORDERFLOW_OIDC_CLIENT_ID` (ver `nginx.conf.template`).

## Testes e2e

Rodam sem backend e sem Keycloak:

- `e2e/support/fake-idp.ts` responde descoberta, JWKS, autorização (exige PKCE S256), token e logout. Os tokens são JWT RS256 assinados com uma chave gerada a cada execução.
- `e2e/support/fake-api.ts` imita os contratos da API com estado: projeção atrasada, saga de estoque, idempotência e `If-Match`.
- Cada tela, inclusive os diálogos, passa pelo axe (WCAG 2.2 AA) nos temas claro e escuro.
- A API falsa valida cada requisição e resposta contra `docs/openapi.yaml`; um desvio falha o teste.

`e2e-stack/` (`npm run e2e:stack` ou `make test-e2e-stack`) roda os fluxos críticos contra a stack real: Keycloak, API e SPA em `http://localhost:4200`. As senhas dos usuários de dev vêm do `.env` da raiz. No CI, o job `e2e-stack` sobe tudo sozinho.

## Contrato da API

`src/testing/openapi-contract.ts` carrega `docs/openapi.yaml`, gerado e verificado no build do backend, e fecha os objetos (campos extras são erro). `src/app/core/api/contract.spec.ts` valida as fábricas de resposta usadas em todos os testes e os corpos que os clientes HTTP enviam.
