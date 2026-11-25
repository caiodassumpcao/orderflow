import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'of-forbidden',
  imports: [RouterLink],
  template: `
    <section class="card card-body">
      <p class="overline">Erro 403</p>
      <h1>Acesso negado</h1>
      <p class="muted">Sua conta não tem permissão para acessar esta página.</p>
      <p><a routerLink="/">Voltar para o início</a></p>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Forbidden {}
