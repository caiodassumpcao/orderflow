import { LOCALE_ID, Provider } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { TitleStrategy, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TEST_CONFIG, fakeAuth, provideFakeAuth } from '../testing/test-support';
import { appConfig } from './app.config';
import { routes } from './app.routes';
import { APP_CONFIG } from './core/config/app-config';
import { PageTitleStrategy } from './core/page-title-strategy';
import { Forbidden } from './features/forbidden/forbidden';
import { Home } from './features/home/home';

describe('appConfig', () => {
  it('provides the runtime configuration and the Brazilian locale', () => {
    const providers = appConfig(TEST_CONFIG, 'https://app.test').providers as Provider[];

    expect(providers).toContainEqual({ provide: APP_CONFIG, useValue: TEST_CONFIG });
    expect(providers).toContainEqual({ provide: LOCALE_ID, useValue: 'pt-BR' });
  });
});

describe('routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: TitleStrategy, useClass: PageTitleStrategy },
        provideFakeAuth(fakeAuth()),
      ],
    });
  });

  it('lazy-loads the home page at the root path with its page title', async () => {
    const harness = await RouterTestingHarness.create();

    const page = await harness.navigateByUrl('/', Home);

    expect(page).toBeInstanceOf(Home);
    expect(TestBed.inject(Title).getTitle()).toBe('Início · OrderFlow');
  });

  it('redirects unknown paths to the home page', async () => {
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/rota-inexistente', Home);

    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toContain('OrderFlow');
  });

  it('has an access denied page', async () => {
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/acesso-negado', Forbidden);

    expect(TestBed.inject(Title).getTitle()).toBe('Acesso negado · OrderFlow');
    expect(harness.routeNativeElement?.textContent).toContain('não tem permissão');
  });
});
