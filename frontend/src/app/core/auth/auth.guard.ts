import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Exige sessão; opcionalmente exige uma das permissões em route.data.permissions. A autorização real é do backend. */
export const authGuard: CanActivateFn = route => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }
  const required = route.data?.['permissions'] as string[] | undefined;
  if (required?.length && !auth.hasAny(...required)) {
    return router.createUrlTree(['/']);
  }
  return true;
};
