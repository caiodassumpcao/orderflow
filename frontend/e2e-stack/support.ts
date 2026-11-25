import { Page, expect } from '@playwright/test';

export interface DevUser {
  readonly username: string;
  readonly password: string;
  readonly name: string;
}

function required(variable: string): string {
  const value = process.env[variable];
  if (!value) {
    throw new Error(`${variable} não definido (ambiente ou .env da raiz)`);
  }
  return value;
}

export const devUser = (): DevUser => ({
  username: 'dev-user',
  password: required('ORDERFLOW_DEV_USER_PASSWORD'),
  name: 'Dev User',
});

export const devAdmin = (): DevUser => ({
  username: 'dev-admin',
  password: required('ORDERFLOW_DEV_ADMIN_PASSWORD'),
  name: 'Dev Admin',
});

/** Abre uma rota protegida e entra pela tela de login do Keycloak (Authorization Code + PKCE). */
export async function signIn(page: Page, path: string, user: DevUser): Promise<void> {
  await page.goto(path);
  await page.waitForURL(/\/realms\/orderflow\/protocol\/openid-connect\/auth/);
  const authorize = new URL(page.url());
  expect(authorize.searchParams.get('code_challenge_method')).toBe('S256');
  await page.locator('#username').fill(user.username);
  await page.locator('#password').fill(user.password);
  await page.locator('#kc-login').click();
  await page.waitForURL((url) => url.pathname === path);
  await expect(page.getByText(user.name)).toBeVisible();
}

export async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Sair' }).click();
  await expect(page.getByRole('button', { name: 'Entrar' }).first()).toBeVisible();
}

/** Cria um pedido de um produto e devolve o id (da URL do detalhe). */
export async function placeOrder(page: Page, sku: string, quantity: number): Promise<string> {
  await page.goto('/pedidos/novo');
  await page.getByLabel('Produto').selectOption(sku);
  await page.getByLabel('Quantidade').fill(String(quantity));
  await page.getByRole('button', { name: 'Criar pedido' }).click();
  await page.waitForURL(/\/pedidos\/[0-9a-f-]{36}$/);
  return page.url().split('/').pop() as string;
}
