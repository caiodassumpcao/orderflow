import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import {
  DEFAULT_CURRENCY_CODE,
  EnvironmentProviders,
  LOCALE_ID,
  Provider,
  signal,
} from '@angular/core';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import {
  AdminOrderSummary,
  CatalogItem,
  DashboardSummary,
  Order,
  OrderSummary,
  StockItem,
} from '../app/core/api/models';
import { AuthService } from '../app/core/auth/auth.service';
import { APP_CONFIG, AppConfig } from '../app/core/config/app-config';

registerLocaleData(localePt);

export const API = 'http://api.test';

export const TEST_CONFIG: AppConfig = {
  apiUrl: API,
  oidc: { authority: 'http://idp.test/realms/orderflow', clientId: 'orderflow-spa' },
};

/** Sem animações: diálogos e snackbars abrem e fecham na hora (jsdom não dispara animationend). */
export const NO_ANIMATIONS: Provider = {
  provide: MATERIAL_ANIMATIONS,
  useValue: { animationsDisabled: true },
};

/** HTTP de teste, configuração, locale pt-BR, roteador vazio e Material sem animações. */
export function testProviders(): (Provider | EnvironmentProviders)[] {
  return [
    NO_ANIMATIONS,
    provideHttpClient(),
    provideHttpClientTesting(),
    provideRouter([]),
    { provide: APP_CONFIG, useValue: TEST_CONFIG },
    { provide: LOCALE_ID, useValue: 'pt-BR' },
    { provide: DEFAULT_CURRENCY_CODE, useValue: 'BRL' },
  ];
}

export interface FakeAuth {
  readonly isAuthenticated: ReturnType<typeof signal<boolean>>;
  readonly displayName: ReturnType<typeof signal<string>>;
  readonly isCustomer: ReturnType<typeof signal<boolean>>;
  readonly isAdmin: ReturnType<typeof signal<boolean>>;
  readonly loginUnavailable: ReturnType<typeof signal<boolean>>;
  readonly login: jest.Mock;
  readonly logout: jest.Mock;
}

export function fakeAuth(
  state: { authenticated?: boolean; customer?: boolean; admin?: boolean; name?: string } = {},
): FakeAuth {
  return {
    isAuthenticated: signal(state.authenticated ?? false),
    displayName: signal(state.name ?? ''),
    isCustomer: signal(state.customer ?? false),
    isAdmin: signal(state.admin ?? false),
    loginUnavailable: signal(false),
    login: jest.fn(),
    logout: jest.fn(),
  };
}

export function provideFakeAuth(auth: FakeAuth): Provider {
  return { provide: AuthService, useValue: auth };
}

export const ORDER_ID = '01a20f13-6a05-73f7-9b74-d102e42e88ab';

export function anOrder(overrides: Partial<Order> = {}): Order {
  return {
    id: ORDER_ID,
    status: 'CREATED',
    currency: 'BRL',
    total: 39.8,
    items: [{ sku: 'SKU-001', quantity: 2, unitPrice: 19.9, total: 39.8 }],
    cancellationReason: null,
    createdAt: '2026-11-25T12:00:00Z',
    updatedAt: '2026-11-25T12:00:00Z',
    ...overrides,
  };
}

export function aSummary(overrides: Partial<OrderSummary> = {}): OrderSummary {
  return {
    id: ORDER_ID,
    status: 'CONFIRMED',
    currency: 'BRL',
    total: 39.8,
    itemCount: 1,
    createdAt: '2026-11-25T12:00:00Z',
    updatedAt: '2026-11-25T12:00:01Z',
    ...overrides,
  };
}

export function anAdminSummary(overrides: Partial<AdminOrderSummary> = {}): AdminOrderSummary {
  return { ...aSummary(), customerId: 'customer-1', ...overrides };
}

export function aCatalogItem(overrides: Partial<CatalogItem> = {}): CatalogItem {
  return {
    sku: 'SKU-001',
    name: 'Caneca de cerâmica',
    currency: 'BRL',
    unitPrice: 19.9,
    inStock: true,
    ...overrides,
  };
}

export function aStockItem(overrides: Partial<StockItem> = {}): StockItem {
  return {
    sku: 'SKU-001',
    name: 'Caneca de cerâmica',
    currency: 'BRL',
    unitPrice: 19.9,
    available: 10,
    reserved: 2,
    version: 3,
    ...overrides,
  };
}

export function aDashboard(overrides: Partial<DashboardSummary> = {}): DashboardSummary {
  return {
    generatedAt: '2026-11-25T12:00:00Z',
    timeZone: 'America/Sao_Paulo',
    totalOrders: 9,
    ordersByStatus: { CREATED: 1, STOCK_RESERVED: 1, CONFIRMED: 3, SHIPPED: 2, CANCELLED: 2 },
    confirmedRevenue: [{ currency: 'BRL', amount: 150.5 }],
    daily: [
      { date: '2026-11-24', orders: 4, cancelled: 1 },
      { date: '2026-11-25', orders: 2, cancelled: 0 },
    ],
    ...overrides,
  };
}

/** Corpo de Problem Details como a API devolve. */
export function problem(type: string, status: number, extra: Record<string, unknown> = {}) {
  return {
    type: `https://github.com/caiodassumpcao/orderflow/blob/main/docs/api/problems.md#${type}`,
    title: type,
    status,
    correlationId: 'corr-1',
    ...extra,
  };
}
