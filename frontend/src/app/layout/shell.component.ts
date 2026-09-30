import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { BreakpointObserver } from '@angular/cdk/layout';
import { Router, RouterLink, RouterLinkActive, RouterOutlet, NavigationEnd } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatBadgeModule } from '@angular/material/badge';
import { MatTooltipModule } from '@angular/material/tooltip';
import { filter, interval, Subscription } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { PlatformApi } from '../core/api/platform-api.service';
import { AiApi } from '../core/api/ai-api.service';
import { ROLE_LABELS } from '../core/labels';

interface NavItem { label: string; icon: string; link: string; permissions?: string[]; exact?: boolean; }
interface NavGroup { title: string; items: NavItem[]; }

const NAV: NavGroup[] = [
  { title: 'Demandas', items: [
    { label: 'Início', icon: 'home', link: '/', exact: true },
    { label: 'Nova demanda', icon: 'add_circle', link: '/demands/new', permissions: ['DEMAND_CREATE'] },
    { label: 'Minhas demandas', icon: 'assignment_ind', link: '/demands/mine', permissions: ['DEMAND_VIEW_OWN'] },
  ]},
  { title: 'PMO', items: [
    { label: 'Kanban', icon: 'view_kanban', link: '/pmo/board', permissions: ['DEMAND_VIEW_ALL'] },
    { label: 'Todas as demandas', icon: 'table_rows', link: '/pmo/table', permissions: ['DEMAND_VIEW_ALL'] },
    { label: 'Aprovações', icon: 'task_alt', link: '/approvals', permissions: ['APPROVAL_DECIDE'] },
    { label: 'Execução técnica', icon: 'engineering', link: '/executions', permissions: ['EXECUTION_VIEW'] },
  ]},
  { title: 'Gestão', items: [
    { label: 'Dashboard', icon: 'insights', link: '/dashboard', permissions: ['DASHBOARD_VIEW'] },
    { label: 'Auditoria', icon: 'history', link: '/audit', permissions: ['AUDIT_VIEW'] },
  ]},
  { title: 'Administração', items: [
    { label: 'Usuários', icon: 'group', link: '/admin/users', permissions: ['ADMIN_USERS'] },
    { label: 'Projetos', icon: 'folder_special', link: '/admin/projects', permissions: ['ADMIN_CONFIG'] },
    { label: 'Workflows e aprovações', icon: 'account_tree', link: '/admin/workflows', permissions: ['ADMIN_CONFIG'] },
    { label: 'Catálogos', icon: 'tune', link: '/admin/catalog', permissions: ['ADMIN_CONFIG'] },
    { label: 'Importar legado', icon: 'upload_file', link: '/admin/legacy', permissions: ['LEGACY_IMPORT'] },
    { label: 'Agentes de IA', icon: 'smart_toy', link: '/admin/agents', permissions: ['ADMIN_CONFIG'] },
  ]},
];

@Component({
  selector: 'dh-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatToolbarModule, MatListModule, MatIconModule,
    MatButtonModule, MatMenuModule, MatBadgeModule, MatTooltipModule],
  template: `
    <a class="skip-link" href="#main">Pular para o conteúdo</a>
    <mat-sidenav-container class="container">
      <mat-sidenav #nav [mode]="isMobile() ? 'over' : 'side'" [opened]="!isMobile()" class="sidenav" role="navigation" aria-label="Navegação principal">
        <div class="brand">
          <mat-icon>hub</mat-icon>
          <div><div class="brand-name">Demand Hub</div><div class="brand-sub">Transformação de Redes</div></div>
        </div>
        @for (group of groups(); track group.title) {
          <div class="group-title">{{ group.title }}</div>
          <mat-nav-list>
            @for (item of group.items; track item.link) {
              <a mat-list-item [routerLink]="item.link" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: !!item.exact }"
                 (click)="isMobile() && nav.close()">
                <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
                <span matListItemTitle>{{ item.label }}</span>
              </a>
            }
          </mat-nav-list>
        }
        @if (aiMock()) {
          <div class="alert mock nav-alert small"><mat-icon>science</mat-icon><span>IA em modo MOCK (heurística local). Configure AI_PROVIDER para usar um provedor real.</span></div>
        }
      </mat-sidenav>

      <mat-sidenav-content>
        <mat-toolbar class="toolbar">
          @if (isMobile()) {
            <button mat-icon-button (click)="nav.toggle()" aria-label="Abrir menu"><mat-icon>menu</mat-icon></button>
          }
          <span class="spacer"></span>
          <button mat-icon-button routerLink="/notifications" aria-label="Notificações" matTooltip="Notificações">
            <mat-icon [matBadge]="unread() || null" matBadgeColor="warn" matBadgeSize="small" aria-hidden="false">notifications</mat-icon>
          </button>
          <button mat-button [matMenuTriggerFor]="userMenu" class="user-btn">
            <mat-icon>account_circle</mat-icon>
            <span class="user-name">{{ auth.user()?.fullName }}</span>
          </button>
          <mat-menu #userMenu="matMenu">
            <div class="menu-info" mat-menu-item disabled>
              <div>{{ auth.user()?.email }}</div>
              <div class="small">{{ roles() }}</div>
            </div>
            <button mat-menu-item (click)="auth.logout()"><mat-icon>logout</mat-icon>Sair</button>
          </mat-menu>
        </mat-toolbar>
        <main id="main" tabindex="-1">
          <router-outlet></router-outlet>
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .container { height: 100vh; }
    .sidenav { width: 256px; border-right: 1px solid var(--dh-border); background: #111a33; color: #dfe4f2; }
    .brand { display: flex; align-items: center; gap: 10px; padding: 18px 16px; }
    .brand .mat-icon { color: #8ea2ff; font-size: 30px; width: 30px; height: 30px; }
    .brand-name { font-weight: 700; font-size: 16px; color: #fff; }
    .brand-sub { font-size: 11px; color: #9aa6c7; }
    .group-title { text-transform: uppercase; font-size: 11px; letter-spacing: .06em; color: #7f8bab; padding: 14px 16px 2px; }
    .sidenav .mat-mdc-list-item { color: #dfe4f2; border-radius: 8px; margin: 0 8px; }
    .sidenav ::ng-deep .mat-mdc-list-item .mdc-list-item__primary-text, .sidenav ::ng-deep .mat-mdc-list-item .mat-icon { color: #dfe4f2 !important; }
    .sidenav .active { background: rgba(142,162,255,.18); }
    .nav-alert { margin: 16px 12px; }
    .toolbar { background: #fff; border-bottom: 1px solid var(--dh-border); position: sticky; top: 0; z-index: 5; height: 56px; }
    .user-btn .user-name { margin-left: 6px; }
    @media (max-width: 600px) { .user-name { display: none; } }
    main:focus { outline: none; }
    .skip-link { position: absolute; left: -999px; top: 8px; z-index: 100; background: #fff; padding: 8px; }
    .skip-link:focus { left: 8px; }
    .menu-info { line-height: 1.3; height: auto !important; padding: 8px 16px; }
  `]
})
export class ShellComponent implements OnInit, OnDestroy {
  auth = inject(AuthService);
  private platform = inject(PlatformApi);
  private ai = inject(AiApi);
  private router = inject(Router);
  private breakpoints = inject(BreakpointObserver);
  private subs = new Subscription();

  isMobile = signal(false);
  unread = signal(0);
  aiMock = signal(false);

  groups = computed(() => NAV
    .map(g => ({ ...g, items: g.items.filter(i => !i.permissions || this.auth.hasAny(...i.permissions)) }))
    .filter(g => g.items.length > 0));

  roles = computed(() => (this.auth.user()?.roles ?? []).map(r => ROLE_LABELS[r] ?? r).join(', '));

  ngOnInit(): void {
    this.subs.add(this.breakpoints.observe('(max-width: 960px)').subscribe(r => this.isMobile.set(r.matches)));
    this.refreshUnread();
    this.subs.add(interval(60000).subscribe(() => this.refreshUnread()));
    this.subs.add(this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => this.refreshUnread()));
    this.ai.status().subscribe({ next: s => this.aiMock.set(s.mock), error: () => {} });
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  private refreshUnread(): void {
    this.platform.unread().subscribe({ next: r => this.unread.set(r.unread), error: () => {} });
  }
}
