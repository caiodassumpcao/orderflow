import { ChangeDetectionStrategy, Component, ElementRef, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, skip } from 'rxjs';
import { AuthService } from './core/auth/auth.service';
import { APP_NAME } from './core/page-title-strategy';

@Component({
  selector: 'of-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly appName = APP_NAME;
  protected readonly year = new Date().getFullYear();

  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');

  constructor() {
    // Numa SPA a página não recarrega: depois de cada navegação (exceto a primeira), o foco vai
    // para o conteúdo, e o leitor de tela começa pela página nova em vez do link clicado.
    inject(Router)
      .events.pipe(
        filter((event) => event instanceof NavigationEnd),
        skip(1),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.main().nativeElement.focus());
  }
}
