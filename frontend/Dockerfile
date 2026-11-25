# syntax=docker/dockerfile:1.7
# Imagem da SPA do OrderFlow. Build a partir de frontend/: docker build -t orderflow-web .

# --- build: instala dependências e gera o bundle de produção ---
FROM node:24.19.0-alpine AS build
WORKDIR /workspace

COPY package.json package-lock.json .npmrc ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit

COPY . .
RUN npm run build

# --- runtime: nginx sem privilégios servindo apenas os arquivos estáticos ---
# Mainline: a stable 1.30.6 tem CVEs críticas corrigidas só na 1.30.7, ainda sem imagem publicada
FROM nginxinc/nginx-unprivileged:1.31.7-alpine-slim

# Aplica as correções de segurança do Alpine publicadas depois da imagem base
USER root
RUN apk upgrade --no-cache

# A configuração é gerada na subida a partir do template e das variáveis ORDERFLOW_* abaixo.
# O config.json de dev sai do bundle: quem responde /config.json é o nginx, com os valores do ambiente.
COPY --chown=nginx:nginx nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build --chown=nginx:nginx /workspace/dist/orderflow-web/browser/ /usr/share/nginx/html/
RUN rm /usr/share/nginx/html/config.json

# Endereços públicos da API e do realm OIDC, vistos pelo navegador. Os padrões servem ao ambiente
# local (docker run -p 4200:8080); em outros ambientes, sobrescreva com -e / env do orquestrador.
ENV ORDERFLOW_API_URL=http://localhost:8080 \
    ORDERFLOW_OIDC_AUTHORITY=http://localhost:8180/realms/orderflow \
    ORDERFLOW_OIDC_CLIENT_ID=orderflow-spa \
    NGINX_ENVSUBST_FILTER=^ORDERFLOW_

USER 101:101
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
