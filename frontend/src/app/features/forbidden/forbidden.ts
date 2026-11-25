import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'of-forbidden',
  imports: [RouterLink],
  template: `
    <h1>Acesso negado</h1>
    <p>Sua conta não tem permissão para acessar esta página.</p>
    <p><a routerLink="/">Voltar para o início</a></p>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Forbidden {}
