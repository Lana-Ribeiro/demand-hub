import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, Router, UrlTree, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  const run = (data: Record<string, unknown> = {}) => TestBed.runInInjectionContext(() =>
    authGuard({ data } as unknown as ActivatedRouteSnapshot, {} as RouterStateSnapshot));

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideRouter([])] });
  });

  it('redireciona para o login sem sessão', () => {
    const result = run() as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/login');
  });

  it('bloqueia rota quando falta a permissão exigida', () => {
    const auth = TestBed.inject(AuthService);
    spyOn(auth, 'isAuthenticated').and.returnValue(true);
    spyOn(auth, 'hasAny').and.returnValue(false);
    const result = run({ permissions: ['DEMAND_VIEW_ALL'] }) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/');
  });

  it('libera quando autenticado e com permissão', () => {
    const auth = TestBed.inject(AuthService);
    spyOn(auth, 'isAuthenticated').and.returnValue(true);
    spyOn(auth, 'hasAny').and.returnValue(true);
    expect(run({ permissions: ['DEMAND_VIEW_ALL'] })).toBeTrue();
  });
});
