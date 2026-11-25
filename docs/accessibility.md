# Acessibilidade

Meta: **WCAG 2.2 nível AA** em todas as telas, nos temas claro e escuro.

## Verificação automática

| Onde | O que verifica | Quando roda |
|---|---|---|
| ESLint (`angular-eslint` template accessibility) | `alt`, `label`, foco em elementos interativos, uso de ARIA nos templates | `make lint`, CI |
| Testes unitários (Angular Testing Library) | Telas consultadas pelo papel e pelo nome acessível (`getByRole`, `getByLabelText`); se o nome acessível some, o teste quebra | `make test`, CI |
| E2E com IdP e API simulados (`frontend/e2e`) | axe (WCAG 2.2 AA) em todas as telas e diálogos, temas claro e escuro; skip link; foco; movimento reduzido sem animação em loop | `make test-e2e`, CI |
| E2E na stack real (`frontend/e2e-stack`) | axe nas telas do fluxo de compra e no painel, com Keycloak e API reais | `make test-e2e-stack`, CI (`e2e-stack`) |
| Lighthouse | Acessibilidade, boas práticas e desempenho da imagem de produção | manual, resultado abaixo |

As telas autenticadas são auditadas pelo axe, o mesmo motor que o Lighthouse usa na categoria de acessibilidade. O Lighthouse é rodado na página pública.

### Lighthouse (25/11/2026)

Imagem `orderflow-web` (nginx, build de produção), página inicial, preset desktop, Lighthouse 13.6.0:

| Categoria | Nota |
|---|---|
| Acessibilidade | 100 |
| Boas práticas | 100 |
| Desempenho | 99 (FCP 0,2 s, LCP 0,8 s, TBT 20 ms, CLS 0) |

Para reproduzir: `make docker-build` e `docker run --rm -p 127.0.0.1:4200:8080 orderflow-web:local`. Depois rode `npx lighthouse@13.6.0 http://127.0.0.1:4200/ --preset=desktop`. Use `127.0.0.1`, não `localhost`, se um dev server estiver na mesma porta em IPv6.

## O que a interface garante

- **Estrutura:** landmarks (`header`, `nav`, `main`, `footer`), um `h1` por página, títulos de aba por página ("Página · OrderFlow").
- **Teclado:**
  - skip link para o conteúdo e foco sempre visível (contraste ≥ 3:1 nos dois temas);
  - a cada navegação, o foco vai para o conteúdo principal, porque numa SPA a página não recarrega.
- **Formulários:** todo campo tem rótulo visível. Os erros aparecem associados ao campo (`mat-error`) e são anunciados. Na criação de pedido, ao enviar com erro, o foco vai para o primeiro campo inválido.
- **Tabelas:** `caption` (oculta visualmente), `scope` nos cabeçalhos e rolagem horizontal dentro do próprio contêiner em telas estreitas (reflow em 320 px).
- **Status:** nunca só por cor; o texto do status está sempre no selo. O andamento da saga de estoque é anunciado numa região `role="status"`.
- **Diálogos:** prendem o foco, fecham com Esc e devolvem o foco ao botão que os abriu.
- **Erros da API:** mensagens em `role="alert"`, em português, com o código de suporte em erros do servidor.
- **Movimento reduzido:** com `prefers-reduced-motion`, as animações não se repetem. O indicador de carregamento troca a barra animada por um texto estático. Ele ocupa espaço fixo, então o conteúdo não pula, e não aparece em respostas rápidas.
- **Contraste:** tokens do Material (M3) e cores próprias escolhidas com contraste ≥ 4,5:1 nos temas claro e escuro.

## Roteiro de teste manual

Testes automáticos cobrem por volta de metade dos critérios da WCAG. Os itens abaixo precisam de uma pessoa. Preencha a coluna Resultado ao executar e registre a data.

### Só teclado (sem mouse)

| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 1 | Na página inicial, Tab | Primeiro foco no "Pular para o conteúdo"; Enter leva ao conteúdo | |
| 2 | Entrar e fazer um pedido só com Tab, setas e Enter | Todos os controles alcançáveis, na ordem visual, com foco visível | |
| 3 | Enviar o pedido com um item sem produto | Foco no campo inválido, erro lido | |
| 4 | Cancelar um pedido: abrir o diálogo, Esc, abrir de novo e confirmar | Esc fecha e o foco volta ao botão; confirmar cancela | |

### Zoom e reflow

| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 5 | Zoom de 200% em todas as telas | Nada cortado nem sobreposto; sem rolagem horizontal da página | |
| 6 | Janela com 320 px de largura | Conteúdo em uma coluna; tabelas rolam dentro do contêiner | |

### Leitor de tela (NVDA + Chrome no Windows, ou VoiceOver + Safari no macOS)

| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 7 | Navegar pelos landmarks (NVDA: D) | Banner, navegação "Principal", principal e informações de conteúdo | |
| 8 | Navegar pelos títulos (NVDA: H) | Um `h1` por página; seções com `h2` | |
| 9 | Criar um pedido e esperar a confirmação | Anúncio de "Aguardando a reserva de estoque…" e depois de "Status: Confirmado." | |
| 10 | Erro de API (ex.: desligar a API e recarregar a lista) | A mensagem de erro é lida assim que aparece | |
| 11 | Tabela de pedidos (NVDA: T e setas) | Cabeçalhos lidos com cada célula; links "Pedido xxxx" | |

## Limitações conhecidas

- A tela de login é do Keycloak (tema padrão), fora do alcance destes testes. A revisão do tema entra junto com a Fase 7.1 (Design e UX).
- O seletor de produto da criação de pedido é um `select` nativo. É acessível, mas não tem busca; com catálogos grandes, um combobox com filtro seria melhor.
