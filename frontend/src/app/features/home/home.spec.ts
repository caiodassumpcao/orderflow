import { fireEvent, render, screen } from '@testing-library/angular';
import { fakeAuth, provideFakeAuth, testProviders } from '../../../testing/test-support';
import { Home } from './home';

describe('Home', () => {
  it('renders a single level-one heading and invites visitors to sign in', async () => {
    const auth = fakeAuth();
    await render(Home, { providers: [...testProviders(), provideFakeAuth(auth)] });

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('OrderFlow');
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));
    expect(auth.login).toHaveBeenCalled();
  });

  it('greets customers with their shortcuts', async () => {
    await render(Home, {
      providers: [
        ...testProviders(),
        provideFakeAuth(fakeAuth({ authenticated: true, customer: true, name: 'Ana' })),
      ],
    });

    expect(screen.getByText(/Olá, Ana!/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Fazer um pedido' })).toHaveAttribute(
      'href',
      '/pedidos/novo',
    );
    expect(screen.queryByRole('link', { name: 'Painel da operação' })).not.toBeInTheDocument();
  });

  it('shows the operation shortcuts to admins', async () => {
    await render(Home, {
      providers: [
        ...testProviders(),
        provideFakeAuth(fakeAuth({ authenticated: true, customer: true, admin: true })),
      ],
    });

    expect(screen.getByText(/^Olá!/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Painel da operação' })).toHaveAttribute(
      'href',
      '/admin',
    );
    expect(screen.getByRole('link', { name: 'Estoque' })).toBeInTheDocument();
  });
});
