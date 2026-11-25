---
status: accepted
date: 2026-11-25
---

# ADR-0005: Keycloak para identidade

## Contexto e problema

A API precisa saber quem é o usuário (cliente dono dos pedidos) e o que ele pode fazer (cliente ou operação). A SPA Angular (Fase 6) fará o login. Guardar senhas e emitir tokens na própria aplicação aumenta a superfície de ataque e o trabalho de manutenção.

## Fatores de decisão

- Padrões abertos (OIDC/OAuth 2.0) e tokens verificáveis sem chamada ao provedor a cada requisição.
- Fluxo seguro para SPA (Authorization Code + PKCE, sem segredo no navegador).
- Papéis gerenciados fora do código.
- Rodar localmente no docker-compose, sem conta em serviço externo.

## Opções consideradas

1. **Keycloak** (self-hosted, OIDC).
2. **Provedor gerenciado** (Auth0, Cognito, Entra ID).
3. **Autenticação própria** (usuários e senhas na API, JWT emitido pela aplicação).

## Decisão

Opção 1: **Keycloak 26**, realm `orderflow` versionado em `infra/keycloak/realm-orderflow.json` (senhas dos usuários de dev só via `.env`).

- **Cliente `orderflow-spa`:** público, só Authorization Code + PKCE (S256), sem concessão direta de senha.
- **API como Resource Server:** valida assinatura (JWKS do emissor), expiração e emissor de cada JWT. Além disso, exige que o token tenha sido emitido para um cliente autorizado (`azp`, padrão `orderflow-spa`), para que tokens de outros clientes do mesmo realm não sejam aceitos. O decoder é resolvido sob demanda: a API sobe mesmo com o Keycloak fora do ar.
- **Identidade:** o cliente dono do pedido é sempre o `sub` do token, nunca um campo do corpo.
- **Papéis (RBAC):** papéis do realm `USER` e `ADMIN` (`realm_access.roles`) viram `ROLE_USER` e `ROLE_ADMIN`. Cada endpoint declara o papel exigido com `@PreAuthorize`, e uma regra do ArchUnit quebra o build se algum endpoint ficar sem declaração. A propriedade do recurso (o cliente só vê e cancela os próprios pedidos) é verificada nos casos de uso e responde 404, para não revelar pedidos de terceiros.

| Papel | Pode |
|---|---|
| `USER` | criar, cancelar e consultar os próprios pedidos |
| `ADMIN` | enviar pedidos, consultar estoque, ver todos os pedidos (`/api/v1/admin/**`) |

### Consequências

- **Positivas:** nenhuma senha na aplicação; login, MFA e políticas de senha ficam no Keycloak; tokens validados localmente; autorização explícita e verificada no build.
- **Negativas:** mais um serviço para operar; mudanças no realm exigem reimportar (o `--import-realm` não sobrescreve um realm existente). Por isso a validação do `azp` foi preferida a um mapper de audiência, que exigiria alterar o realm.
- **Neutras:** trocar de provedor OIDC exige só ajustar o conversor de papéis e a configuração do emissor.

## Prós e contras das opções

### Provedor gerenciado

- Bom, porque não há servidor para operar.
- Ruim, porque exige conta externa e não roda offline no docker-compose de desenvolvimento.

### Autenticação própria

- Ruim, porque a aplicação passa a guardar senhas, emitir e revogar tokens e implementar MFA, recuperação de conta e proteção contra força bruta.

## Mais informações

Implementação em `shared.security` (`SecurityConfig`, `KeycloakRealmRoleConverter`, `AuthorizedPartyValidator`). Testes: `AuthorizationIT` (matriz de papéis), `SecurityComponentsTest` e a regra `ENDPOINTS_DECLARE_REQUIRED_ROLE` do ArchUnit.
