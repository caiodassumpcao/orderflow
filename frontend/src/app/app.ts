import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { APP_NAME } from './core/page-title-strategy';

@Component({
  selector: 'of-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly appName = APP_NAME;
  protected readonly year = new Date().getFullYear();
}
