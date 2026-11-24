# Política de Segurança

## Versões suportadas

O projeto ainda não tem release estável. Correções de segurança são aplicadas na branch `develop` e na última versão publicada.

## Como reportar uma vulnerabilidade

**Não abra issue pública.** Use o recurso [Private vulnerability reporting](https://github.com/caiodassumpcao/orderflow/security/advisories/new) do GitHub.

Inclua, se possível:

- descrição e impacto;
- passos para reproduzir ou prova de conceito;
- versão/commit afetado;
- sugestão de correção.

Prazos-alvo: confirmação em até 5 dias úteis, avaliação inicial em até 10 dias úteis. Avisaremos quando a correção for publicada e daremos crédito, se desejado.

## Escopo

Dentro do escopo: código deste repositório (backend, frontend, infra, workflows).
Fora do escopo: credenciais de desenvolvimento de exemplo (`.env.example`), vulnerabilidades em dependências de terceiros já reportadas upstream e ataques que exijam acesso físico.

## Práticas adotadas

- Segredos apenas via variáveis de ambiente; varredura com gitleaks.
- SAST com CodeQL; dependências com OWASP Dependency-Check, `npm audit` e Dependabot; imagens com Trivy.
- Containers com usuário não-root e imagens mínimas.
- Autenticação OIDC (Keycloak) e validação de JWT no backend.
