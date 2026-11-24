import { TestBed } from '@angular/core/testing';
import { Home } from './home';

describe('Home', () => {
  it('renders a single level-one heading', async () => {
    const fixture = TestBed.createComponent(Home);
    await fixture.whenStable();
    const headings = (fixture.nativeElement as HTMLElement).querySelectorAll('h1');

    expect(headings).toHaveLength(1);
    expect(headings[0].textContent).toContain('OrderFlow');
  });
});
