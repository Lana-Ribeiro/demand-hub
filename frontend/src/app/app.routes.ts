import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent), title: 'Entrar — Demand Hub' },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent), title: 'Início — Demand Hub' },
      { path: 'demands/mine', loadComponent: () => import('./features/demands/my-demands.component').then(m => m.MyDemandsComponent), title: 'Minhas demandas' },
      { path: 'demands/new', canActivate: [authGuard], data: { permissions: ['DEMAND_CREATE'] },
        loadComponent: () => import('./features/demands/demand-form.component').then(m => m.DemandFormComponent), title: 'Nova demanda' },
      { path: 'demands/:id/edit', loadComponent: () => import('./features/demands/demand-form.component').then(m => m.DemandFormComponent), title: 'Editar rascunho' },
      { path: 'demands/:id', loadComponent: () => import('./features/demands/detail/demand-detail.component').then(m => m.DemandDetailComponent), title: 'Demanda' },
      { path: 'pmo/board', canActivate: [authGuard], data: { permissions: ['DEMAND_VIEW_ALL'] },
        loadComponent: () => import('./features/pmo/board.component').then(m => m.BoardComponent), title: 'Kanban PMO' },
      { path: 'pmo/table', canActivate: [authGuard], data: { permissions: ['DEMAND_VIEW_ALL'] },
        loadComponent: () => import('./features/pmo/demand-table.component').then(m => m.DemandTableComponent), title: 'Demandas' },
      { path: 'approvals', canActivate: [authGuard], data: { permissions: ['APPROVAL_DECIDE'] },
        loadComponent: () => import('./features/approvals/approvals.component').then(m => m.ApprovalsComponent), title: 'Aprovações' },
      { path: 'executions', canActivate: [authGuard], data: { permissions: ['EXECUTION_VIEW'] },
        loadComponent: () => import('./features/executions/executions.component').then(m => m.ExecutionsComponent), title: 'Execução técnica' },
      { path: 'dashboard', canActivate: [authGuard], data: { permissions: ['DASHBOARD_VIEW'] },
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent), title: 'Dashboard gerencial' },
      { path: 'notifications', loadComponent: () => import('./features/notifications/notifications.component').then(m => m.NotificationsComponent), title: 'Notificações' },
      { path: 'audit', canActivate: [authGuard], data: { permissions: ['AUDIT_VIEW'] },
        loadComponent: () => import('./features/audit/audit.component').then(m => m.AuditComponent), title: 'Auditoria' },
      { path: 'admin/users', canActivate: [authGuard], data: { permissions: ['ADMIN_USERS'] },
        loadComponent: () => import('./features/admin/users.component').then(m => m.UsersComponent), title: 'Usuários' },
      { path: 'admin/projects', canActivate: [authGuard], data: { permissions: ['ADMIN_CONFIG'] },
        loadComponent: () => import('./features/admin/projects.component').then(m => m.ProjectsComponent), title: 'Projetos' },
      { path: 'admin/workflows', canActivate: [authGuard], data: { permissions: ['ADMIN_CONFIG'] },
        loadComponent: () => import('./features/admin/workflows.component').then(m => m.WorkflowsComponent), title: 'Workflows e aprovações' },
      { path: 'admin/catalog', canActivate: [authGuard], data: { permissions: ['ADMIN_CONFIG'] },
        loadComponent: () => import('./features/admin/catalog.component').then(m => m.CatalogAdminComponent), title: 'Catálogos' },
      { path: 'admin/legacy', canActivate: [authGuard], data: { permissions: ['LEGACY_IMPORT'] },
        loadComponent: () => import('./features/admin/legacy.component').then(m => m.LegacyComponent), title: 'Importar legado' },
      { path: 'admin/agents', canActivate: [authGuard], data: { permissions: ['ADMIN_CONFIG', 'AI_USE'] },
        loadComponent: () => import('./features/admin/agents.component').then(m => m.AgentsComponent), title: 'Agentes de IA' },
    ]
  },
  { path: '**', redirectTo: '' }
];
