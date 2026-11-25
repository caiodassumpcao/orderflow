import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localePt from '@angular/common/locales/pt';
import {
  ApplicationConfig,
  DEFAULT_CURRENCY_CODE,
  LOCALE_ID,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { TitleStrategy, provideRouter, withComponentInputBinding } from '@angular/router';
import {
  authInterceptor,
  provideAuth,
  withAppInitializerAuthCheck,
} from 'angular-auth-oidc-client';
import { routes } from './app.routes';
import { oidcConfig } from './core/auth/oidc-config';
import { APP_CONFIG, AppConfig } from './core/config/app-config';
import { PageTitleStrategy } from './core/page-title-strategy';

registerLocaleData(localePt);

/**
 * @param config configuração de runtime (`/config.json`)
 * @param origin origem da SPA, base das URLs de retorno do login e do logout
 */
export function appConfig(config: AppConfig, origin: string): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      provideRouter(routes, withComponentInputBinding()),
      { provide: TitleStrategy, useClass: PageTitleStrategy },
      { provide: APP_CONFIG, useValue: config },
      // Anexa o access token só às rotas de `secureRoutes` (a API).
      provideHttpClient(withInterceptors([authInterceptor()])),
      // Processa o retorno do login (?code=...) antes da primeira navegação.
      provideAuth({ config: oidcConfig(config, origin) }, withAppInitializerAuthCheck()),
      { provide: LOCALE_ID, useValue: 'pt-BR' },
      { provide: DEFAULT_CURRENCY_CODE, useValue: 'BRL' },
    ],
  };
}
