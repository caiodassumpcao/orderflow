import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Router, TitleStrategy, provideRouter } from '@angular/router';
import { PageTitleStrategy } from './page-title-strategy';

@Component({ template: '' })
class Blank {}

describe('PageTitleStrategy', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'pedidos', title: 'Pedidos', component: Blank },
          { path: 'sem-titulo', component: Blank },
        ]),
        { provide: TitleStrategy, useClass: PageTitleStrategy },
      ],
    });
  });

  it('suffixes the route title with the app name', async () => {
    await TestBed.inject(Router).navigateByUrl('/pedidos');

    expect(TestBed.inject(Title).getTitle()).toBe('Pedidos · OrderFlow');
  });

  it('falls back to the app name when the route has no title', async () => {
    await TestBed.inject(Router).navigateByUrl('/sem-titulo');

    expect(TestBed.inject(Title).getTitle()).toBe('OrderFlow');
  });
});
