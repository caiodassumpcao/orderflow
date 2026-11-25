---
status: accepted
date: 2026-11-25
---

# ADR-0009: Login OIDC na própria SPA, com PKCE (sem BFF)

## Contexto e problema

A SPA Angular precisa autenticar o usuário no Keycloak ([ADR-0005](0005-keycloak-identity.md)) e chamar a API com um access token. Onde o fluxo OAuth acontece e onde os tokens ficam define a superfície de ataque (XSS, CSRF), a complexidade da infraestrutura e o que precisa mudar se a SPA e a API passarem a ter outros domínios.

## Fatores de decisão

- Fluxo recomendado para clientes públicos (OAuth 2.0 for Browser-Based Apps): Authorization Code com PKCE, sem implicit nem senha.
- API stateless, já protegida só por `Authorization: Bearer` (sem cookie, sem CSRF; ver Fase 5).
- Imagem da SPA estática (nginx sem root), sem servidor de aplicação próprio.
- Biblioteca mantida e certificada, em vez de implementar o fluxo à mão.

## Opções consideradas

1. **SPA como cliente público OIDC** com Authorization Code + PKCE, tokens no `sessionStorage`, renovação com refresh token.
2. **BFF (Backend for Frontend):** um servidor troca o código pelos tokens e entrega à SPA só um cookie de sessão `HttpOnly`.
3. **Tokens em memória** na SPA, sem storage, com novo login a cada recarga ou renovação por iframe (`prompt=none`).

## Decisão

Opção 1, com a biblioteca **angular-auth-oidc-client** (certificada pela OpenID Foundation):

- Cliente `orderflow-spa`, público, PKCE S256; `redirect_uri` e `post_logout_redirect_uri` na raiz da SPA.
- O id_token é validado no navegador (assinatura pelo JWKS, emissor, audiência, `azp`, nonce, state). A API valida o access token por conta própria.
- Tokens no `sessionStorage` (padrão da biblioteca): somem ao fechar a aba e não são compartilhados entre abas.
- Renovação com refresh token, sem iframe nem cookies de terceiros. Sem `offline_access`, para não criar sessões offline no Keycloak.
- O interceptor só anexa o token às URLs da API (`secureRoutes`); chamadas ao Keycloak e a outros hosts não levam o token.
- Rotas protegidas usam o guard de login automático, que guarda a rota pedida e a retoma depois do login. Os papéis (`realm_access.roles`) são lidos do access token só para decidir o que a interface mostra; a autorização de verdade continua na API.
- URLs da API e do emissor vêm de `/config.json` em runtime (na imagem, gerado das variáveis de ambiente), e a CSP libera `connect-src` só para elas.

### Consequências

- **Positivas:** nenhum componente servidor novo; a SPA continua estática; a API continua sem estado e sem CSRF; trocar de domínio exige só configuração.
- **Negativas:** um XSS na SPA conseguiria ler os tokens do `sessionStorage`. A mitigação é a CSP sem scripts de terceiros nem inline (`script-src 'self'`), templates Angular (que escapam por padrão) e tokens de vida curta (5 min). A revisão final da CSP e a reavaliação do BFF estão na Fase 10.
- **Neutras:** se a autenticação passar a usar cookie (BFF), o CSRF da API precisa ser reativado (nota da Fase 5).

## Prós e contras das opções

### BFF

- Bom, porque os tokens nunca chegam ao JavaScript.
- Ruim, porque exige mais um serviço com estado (sessão), proteção CSRF na API e roteamento da SPA e da API pelo mesmo domínio.

### Tokens só em memória

- Bom, porque não há token em storage.
- Ruim, porque cada recarga exige renovação silenciosa por iframe, que depende de cookies de terceiros (bloqueados por vários navegadores), ou um novo login.

## Mais informações

Configuração em `frontend/src/app/core/auth/oidc-config.ts`. Os testes e2e usam um provedor OIDC falso, que assina tokens RS256 de verdade e confere o PKCE (`frontend/e2e/support/fake-idp.ts`), para testar o fluxo completo no CI sem Keycloak.
