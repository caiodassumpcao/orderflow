import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'of-home',
  templateUrl: './home.html',
  styleUrl: './home.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Home {}
