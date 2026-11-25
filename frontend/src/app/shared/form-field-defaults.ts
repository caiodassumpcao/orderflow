import { Provider } from '@angular/core';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';

/**
 * Campos com contorno em todas as telas com formulário. Fica nas rotas lazy, e não na
 * configuração da aplicação, para o Material de formulários não entrar no bundle inicial.
 */
export const FORM_FIELD_DEFAULTS: Provider = {
  provide: MAT_FORM_FIELD_DEFAULT_OPTIONS,
  useValue: { appearance: 'outline' },
};
