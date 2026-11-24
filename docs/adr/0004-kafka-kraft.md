---
status: accepted
date: 2026-11-24
---

# ADR-0004: Kafka (KRaft) como broker

## Contexto e problema

Os contextos `orders` e `inventory` e o modelo de leitura conversam por eventos (ADR-0001). O broker precisa preservar a ordem dos eventos de um mesmo pedido, permitir vários consumidores independentes do mesmo fluxo e guardar as mensagens por tempo suficiente para reprocessar.

## Fatores de decisão

- Ordem garantida por agregado.
- Vários consumer groups lendo o mesmo tópico, cada um no seu ritmo.
- Retenção e releitura (reprocessar, reconstruir projeções).
- Operação simples em dev e no CI.

## Opções consideradas

1. **Apache Kafka em modo KRaft** (sem ZooKeeper).
2. **RabbitMQ**.
3. **Kafka com ZooKeeper**.

## Decisão

Opção 1: **Apache Kafka 4 em modo KRaft**, imagem oficial `apache/kafka` com versão fixa no docker-compose e no Testcontainers.

- Chave de partição = `aggregateId`: todos os eventos de um pedido vão para a mesma partição, em ordem.
- Tópicos criados pela aplicação; o broker não cria tópicos sozinho (`auto.create.topics.enable=false`), para que um nome errado falhe em vez de criar um tópico vazio.
- Produtor idempotente com `acks=all`.

### Consequências

- **Positivas:** ordem por partição, consumer groups independentes, retenção e releitura nativas; um único container em dev, sem ZooKeeper.
- **Negativas:** mais pesado que um broker de filas para cargas pequenas; retry com atraso não é nativo e depende do Spring Kafka (ADR-0007).
- **Neutras:** produção precisa de 3 brokers e `replication.factor=3`; o fator de replicação é obrigatório na configuração fora de dev.

## Prós e contras das opções

### RabbitMQ

- Bom, porque tem filas com atraso e DLX nativos.
- Ruim, porque a ordem por agregado com vários consumidores exige filas por chave, e não há releitura do histórico.

### Kafka com ZooKeeper

- Ruim, porque o ZooKeeper foi removido no Kafka 4; não há motivo para adotá-lo num projeto novo.
