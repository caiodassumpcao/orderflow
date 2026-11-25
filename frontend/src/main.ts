import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { appConfig } from './app/app.config';
import { loadAppConfig } from './app/core/config/app-config';

/** Sem configuração não há como falar com a API nem com o Keycloak: mostra o erro e para. */
function showStartupError(): void {
  const message = document.createElement('p');
  message.setAttribute('role', 'alert');
  message.textContent =
    'Não foi possível iniciar o OrderFlow: a configuração da aplicação não foi carregada.';
  document.body.replaceChildren(message);
}

loadAppConfig()
  .then((config) => bootstrapApplication(App, appConfig(config, window.location.origin)))
  .catch((error: unknown) => {
    console.error(error);
    showStartupError();
  });
