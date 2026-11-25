import { InjectionToken } from '@angular/core';

/**
 * Configuração de runtime, lida de `/config.json` antes do bootstrap. Em dev vem de
 * `public/config.json`; na imagem nginx é gerada das variáveis de ambiente do container.
 */
export interface AppConfig {
  /** Origem da API, sem barra no fim (ex.: `http://localhost:8080`). */
  readonly apiUrl: string;
  readonly oidc: {
    /** Emissor OIDC (realm do Keycloak), sem barra no fim. */
    readonly authority: string;
    readonly clientId: string;
  };
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');

export class InvalidAppConfigError extends Error {
  constructor(message: string) {
    super(`Configuração inválida: ${message}`);
    this.name = 'InvalidAppConfigError';
  }
}

function requireUrl(value: unknown, field: string): string {
  if (typeof value !== 'string' || value.trim() === '') {
    throw new InvalidAppConfigError(`${field} é obrigatório`);
  }
  let url: URL;
  try {
    url = new URL(value.trim());
  } catch {
    throw new InvalidAppConfigError(`${field} não é uma URL`);
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') {
    throw new InvalidAppConfigError(`${field} precisa usar http ou https`);
  }
  return url.href.replace(/\/+$/, '');
}

/** Valida o JSON recebido: falha cedo, com mensagem clara, se faltar algo. */
export function parseAppConfig(raw: unknown): AppConfig {
  if (typeof raw !== 'object' || raw === null) {
    throw new InvalidAppConfigError('o arquivo não contém um objeto');
  }
  const { apiUrl, oidc } = raw as { apiUrl?: unknown; oidc?: Record<string, unknown> };
  const clientId = oidc?.['clientId'];
  if (typeof clientId !== 'string' || clientId.trim() === '') {
    throw new InvalidAppConfigError('oidc.clientId é obrigatório');
  }
  return {
    apiUrl: requireUrl(apiUrl, 'apiUrl'),
    oidc: {
      authority: requireUrl(oidc?.['authority'], 'oidc.authority'),
      clientId: clientId.trim(),
    },
  };
}

export async function loadAppConfig(fetchFn: typeof fetch = fetch): Promise<AppConfig> {
  const response = await fetchFn('/config.json', { cache: 'no-store' });
  if (!response.ok) {
    throw new InvalidAppConfigError(`/config.json respondeu ${response.status}`);
  }
  return parseAppConfig(await response.json());
}
