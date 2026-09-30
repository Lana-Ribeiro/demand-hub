import { HttpErrorResponse } from '@angular/common/http';
import { NotifyService } from './notify.service';

describe('NotifyService.message', () => {
  it('usa o detail do ProblemDetail e lista os gates não cumpridos', () => {
    const err = new HttpErrorResponse({ status: 422, error: { detail: 'Avanço bloqueado.', unmetRequirements: ['Defina a prioridade.'] } });
    expect(NotifyService.message(err)).toBe('Avanço bloqueado. Defina a prioridade.');
  });

  it('informa indisponibilidade do servidor', () => {
    expect(NotifyService.message(new HttpErrorResponse({ status: 0 }))).toContain('Servidor indisponível');
  });

  it('extrai erros por campo', () => {
    const err = new HttpErrorResponse({ status: 422, error: { fieldErrors: { title: 'Obrigatório' } } });
    expect(NotifyService.fieldErrors(err)).toEqual({ title: 'Obrigatório' });
  });
});
