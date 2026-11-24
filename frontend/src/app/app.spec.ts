import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;
  });

  it('renders the page landmarks', () => {
    expect(element.querySelector('header')).not.toBeNull();
    expect(element.querySelector('main')).not.toBeNull();
    expect(element.querySelector('footer')).not.toBeNull();
  });

  it('offers a skip link that targets the main content', () => {
    const skipLink = element.querySelector<HTMLAnchorElement>('a.skip-link');
    const main = element.querySelector('main');

    expect(skipLink?.getAttribute('href')).toBe(`#${main?.id}`);
    expect(main?.getAttribute('tabindex')).toBe('-1');
  });
});
