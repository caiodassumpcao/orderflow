import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ANIMATIONS } from '../../../testing/test-support';
import { LoadingIndicator, MIN_VISIBLE_MS, SHOW_DELAY_MS } from './loading-indicator';

describe('LoadingIndicator', () => {
  let fixture: ComponentFixture<LoadingIndicator>;

  beforeEach(() => {
    jest.useFakeTimers();
    TestBed.configureTestingModule({ providers: [NO_ANIMATIONS] });
  });

  afterEach(() => jest.useRealTimers());

  function setup(active: boolean) {
    fixture = TestBed.createComponent(LoadingIndicator);
    fixture.componentRef.setInput('label', 'Carregando pedidos');
    setActive(active);
  }

  function setActive(active: boolean) {
    fixture.componentRef.setInput('active', active);
    fixture.detectChanges();
  }

  function advance(ms: number) {
    jest.advanceTimersByTime(ms);
    fixture.detectChanges();
  }

  const element = () => fixture.nativeElement as HTMLElement;
  const bar = () => element().querySelector('[role="progressbar"]');

  it('stays hidden for quick loads, so nothing blinks', () => {
    setup(true);

    advance(SHOW_DELAY_MS - 50);
    setActive(false);
    advance(SHOW_DELAY_MS);

    expect(bar()).toBeNull();
  });

  it('appears after the delay with a text alternative for reduced motion', () => {
    setup(true);

    advance(SHOW_DELAY_MS);

    expect(bar()?.getAttribute('aria-label')).toBe('Carregando pedidos');
    expect(element().textContent).toContain('Carregando pedidos…');
  });

  it('stays visible for a minimum time once shown', () => {
    setup(true);
    advance(SHOW_DELAY_MS);

    setActive(false);
    advance(MIN_VISIBLE_MS - 100);
    expect(bar()).not.toBeNull();

    advance(100);
    expect(bar()).toBeNull();
  });

  it('keeps showing when loading starts again while it is going away', () => {
    setup(true);
    advance(SHOW_DELAY_MS);
    setActive(false);
    advance(50);

    setActive(true);
    advance(MIN_VISIBLE_MS + SHOW_DELAY_MS);

    expect(bar()).not.toBeNull();
  });

  it('never shows while idle', () => {
    setup(false);

    advance(MIN_VISIBLE_MS * 2);

    expect(bar()).toBeNull();
  });
});
