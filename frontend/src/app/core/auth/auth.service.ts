import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { LoginResponse, User } from '../models';

const STORAGE_KEY = 'dh.session';

interface Session { token: string; expiresAt: string; user: User; }

/** Sessão do usuário. Token em sessionStorage (escopo da aba); permissões vêm do backend e só controlam a UI. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private session = signal<Session | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => !!this.session());

  get token(): string | null {
    return this.session()?.token ?? null;
  }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { email, password }).pipe(
      tap(res => this.store({ token: res.token, expiresAt: res.expiresAt, user: res.user }))
    );
  }

  refreshUser(): void {
    this.http.get<User>('/api/auth/me').subscribe(user => {
      const s = this.session();
      if (s) this.store({ ...s, user });
    });
  }

  logout(redirect = true): void {
    try { sessionStorage.removeItem(STORAGE_KEY); } catch { /* armazenamento indisponível */ }
    this.session.set(null);
    if (redirect) this.router.navigate(['/login']);
  }

  has(permission: string): boolean {
    return this.user()?.permissions.includes(permission) ?? false;
  }

  hasAny(...permissions: string[]): boolean {
    return permissions.some(p => this.has(p));
  }

  hasRole(role: string): boolean {
    return this.user()?.roles.includes(role) ?? false;
  }

  private store(s: Session): void {
    try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(s)); } catch { /* armazenamento indisponível */ }
    this.session.set(s);
  }

  private restore(): Session | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const s = JSON.parse(raw) as Session;
      return new Date(s.expiresAt).getTime() > Date.now() ? s : null;
    } catch {
      return null;
    }
  }
}
