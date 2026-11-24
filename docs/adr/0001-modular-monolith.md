---
status: accepted
date: 2026-11-24
---

# ADR-0001: Monólito modular em vez de microsserviços

## Contexto e problema

O OrderFlow tem dois bounded contexts, `orders` e `inventory`, que conversam por eventos (saga de reserva de estoque) e aplicam CQRS no contexto de pedidos. O projeto é mantido por uma pessoa e precisa demonstrar mensageria confiável (outbox, idempotência, retry/DLQ) sem que a infraestrutura de deploy domine o esforço.

Como organizar o backend para manter os contextos isolados e a comunicação assíncrona real, sem pagar o custo operacional de vários serviços desde o início?

## Fatores de decisão

- Isolamento entre contextos verificável automaticamente, não só por convenção.
- Comunicação entre contextos pelos mesmos mecanismos de produção (Kafka, outbox, consumers idempotentes).
- Um único build, deploy e pipeline enquanto o domínio ainda está em evolução.
- Caminho de extração para serviço próprio sem reescrever o domínio.

## Opções consideradas

1. **Monólito modular** com contextos isolados por pacote e eventos Kafka entre eles.
2. **Microsserviços** desde o início (um serviço e um banco por contexto).
3. **Monólito em camadas** tradicional, sem fronteira explícita entre contextos.

## Decisão

Opção 1: **monólito modular**, com arquitetura hexagonal por contexto (`domain` → `application` → `adapter`) sob `com.caiodassumpcao.orderflow`.

- Cada contexto publica e consome eventos via Kafka, como faria entre serviços. Não há chamada direta entre os pacotes de `orders` e `inventory`.
- Escrita (`command`) e leitura (`query`) ficam em schemas separados do mesmo PostgreSQL.
- As fronteiras são verificadas por testes ArchUnit: o domínio não depende de Spring, JPA nem Hibernate, nem das camadas externas; a aplicação não depende dos adapters.

### Consequências

- **Positivas:** um artefato e um pipeline; refatorações entre contextos continuam baratas; os padrões de mensageria são exercitados de verdade, com Testcontainers.
- **Negativas:** os contextos escalam e são implantados juntos; um banco compartilhado exige disciplina de schemas, que as migrations e o ArchUnit reforçam.
- **Neutras:** extrair um contexto para serviço próprio passa a ser mover o pacote e o schema, porque o contrato entre contextos já são os eventos versionados.

## Prós e contras das opções

### Microsserviços

- Bom, porque permite deploy e escala independentes.
- Ruim, porque multiplica pipelines, bancos, observabilidade e configuração para um time de uma pessoa.
- Ruim, porque as fronteiras ficam caras de mudar antes de o domínio se estabilizar.

### Monólito em camadas

- Bom, porque é o mais simples de começar.
- Ruim, porque nada impede o acoplamento entre `orders` e `inventory`, e a saga vira chamada síncrona.
- Ruim, porque não demonstra os padrões de mensageria que são objetivo do projeto.

## Mais informações

Regras de arquitetura: `backend/src/test/java/com/caiodassumpcao/orderflow/architecture/`. Revisitar esta decisão se um contexto precisar de escala ou cadência de deploy próprias.
