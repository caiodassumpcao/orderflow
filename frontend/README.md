# OrderFlow — frontend

SPA Angular 22 (standalone, signals, `OnPush`, zoneless). Visão geral e roadmap no [README da raiz](../README.md).

| Comando                           | O que faz                                    |
| --------------------------------- | -------------------------------------------- |
| `npm start`                       | Dev server em `http://localhost:4200`        |
| `npm run build`                   | Build de produção em `dist/`                 |
| `npm test` / `npm run test:ci`    | Testes unitários (Jest) / com cobertura      |
| `npm run lint` / `npm run format` | ESLint + Prettier: verifica / corrige        |
| `npm run e2e`                     | Playwright + axe (sobe o dev server sozinho) |

Estrutura: `src/app/core` (serviços e infraestrutura do app), `src/app/shared` (componentes reutilizáveis), `src/app/features/*` (telas com rotas lazy).
