import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/angular';
import { FakeAuth, fakeAuth, provideFakeAuth } from '../testing/test-support';
import { App } from './app';

@Component({ selector: 'of-blank', template: '<p>Página</p>' })
class Blank {}

describe('App', () => {
  async function setup(auth: FakeAuth) {
    await render(App, {
      providers: [
        provideRouter([
          { path: '', component: Blank },
          { path: 'outra', component: Blank },
        ]),
        provideFakeAuth(auth),
      ],
    });
  }

  it('renders the page landmarks and a skip link to the main content', async () => {
    await setup(fakeAuth());

    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
    const main = screen.getByRole('main');
    expect(screen.getByRole('link', { name: 'Pular para o conteúdo' })).toHaveAttribute(
      'href',
      `#${main.id}`,
    );
    expect(main).toHaveAttribute('tabindex', '-1');
  });

  it('offers sign in to visitors, without navigation', async () => {
    const auth = fakeAuth();
    await setup(auth);

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));
    expect(auth.login).toHaveBeenCalled();
  });

  it('warns when the login service is unavailable and lets the user retry', async () => {
    const auth = fakeAuth();
    await setup(auth);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    auth.loginUnavailable.set(true);

    expect(await screen.findByRole('alert')).toHaveTextContent('serviço de login');
    fireEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));
    expect(auth.login).toHaveBeenCalled();
  });

  it('shows customers their links, their name and sign out', async () => {
    const auth = fakeAuth({ authenticated: true, customer: true, name: 'Ana' });
    await setup(auth);

    const nav = screen.getByRole('navigation', { name: 'Principal' });
    expect(within(nav).getByRole('link', { name: 'Meus pedidos' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'Estoque' })).not.toBeInTheDocument();
    expect(screen.getByText('Ana')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Sair' }));
    expect(auth.logout).toHaveBeenCalled();
  });

  it('shows admins the operation links', async () => {
    await setup(fakeAuth({ authenticated: true, customer: true, admin: true }));

    const nav = screen.getByRole('navigation', { name: 'Principal' });
    expect(within(nav).getByRole('link', { name: 'Painel' })).toHaveAttribute('href', '/admin');
    expect(within(nav).getByRole('link', { name: 'Todos os pedidos' })).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: 'Estoque' })).toBeInTheDocument();
  });

  it('moves focus to the main content after navigating', async () => {
    await setup(fakeAuth());
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/');

    await router.navigateByUrl('/outra');

    await waitFor(() => expect(screen.getByRole('main')).toHaveFocus());
  });
});
