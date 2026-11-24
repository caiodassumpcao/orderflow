import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { RouterTestingHarness } from '@angular/router/testing';
import { appConfig } from './app.config';
import { Home } from './features/home/home';

describe('appConfig', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: appConfig.providers });
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
});
