import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/** Feedback visual padronizado. Extrai mensagens de ProblemDetail (RFC 7807) do backend. */
@Injectable({ providedIn: 'root' })
export class NotifyService {
  private snack = inject(MatSnackBar);

  success(message: string): void {
    this.snack.open(message, 'OK', { duration: 3500, panelClass: 'snack-success' });
  }

  error(err: unknown, fallback = 'Não foi possível concluir a operação.'): void {
    this.snack.open(NotifyService.message(err, fallback), 'Fechar', { duration: 7000, panelClass: 'snack-error' });
  }

  static message(err: unknown, fallback = 'Não foi possível concluir a operação.'): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 0) return 'Servidor indisponível. Verifique sua conexão.';
      const body = err.error as { detail?: string; unmetRequirements?: string[]; fieldErrors?: Record<string, string> } | null;
      if (body?.unmetRequirements?.length) return `${body.detail} ${body.unmetRequirements.join(' ')}`;
      if (body?.detail) return body.detail;
    }
    return fallback;
  }

  static fieldErrors(err: unknown): Record<string, string> {
    if (err instanceof HttpErrorResponse) {
      return (err.error?.fieldErrors as Record<string, string>) ?? {};
    }
    return {};
  }
}
