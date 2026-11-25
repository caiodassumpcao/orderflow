import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { Observable, firstValueFrom, of } from 'rxjs';
import { AuthService } from './auth.service';
import { FORBIDDEN_PATH, roleGuard } from './role.guard';

describe('roleGuard', () => {
  function run(hasRole: boolean): Promise<boolean | UrlTree> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { hasRole: jest.fn(() => of(hasRole)) } },
      ],
    });
    const result = TestBed.runInInjectionContext(() =>
      roleGuard('ADMIN')({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;
    return firstValueFrom(result);
  }

  it('lets users with the role through', async () => {
    await expect(run(true)).resolves.toBe(true);
  });

  it('sends users without the role to the forbidden page', async () => {
    const result = await run(false);

    expect(result).toBeInstanceOf(UrlTree);
    expect(result.toString()).toBe(FORBIDDEN_PATH);
  });
});
