import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ApprovalView, AuditLog, BoardColumn, Comment, DemandDetail, DemandSummary, DocumentView, InformationRequest, Page, PendingApproval
} from '../models';

export interface DemandFilters {
  q?: string; projectId?: number | null; type?: string | null; priority?: string | null; stage?: string | null;
  category?: string | null; lifecycle?: string | null; ownerId?: string | null; impact?: string | null;
  from?: string | null; to?: string | null; pendingApproval?: boolean | null; includeLegacy?: boolean;
  page?: number; size?: number; sort?: string;
}

@Injectable({ providedIn: 'root' })
export class DemandApi {
  private http = inject(HttpClient);

  search(f: DemandFilters): Observable<Page<DemandSummary>> {
    let params = new HttpParams();
    Object.entries(f).forEach(([k, v]) => {
      if (v !== null && v !== undefined && v !== '') params = params.set(k, String(v));
    });
    return this.http.get<Page<DemandSummary>>('/api/demands', { params });
  }

  mine(): Observable<DemandSummary[]> { return this.http.get<DemandSummary[]>('/api/demands/mine'); }

  board(f: DemandFilters): Observable<BoardColumn[]> {
    let params = new HttpParams();
    Object.entries(f).forEach(([k, v]) => { if (v !== null && v !== undefined && v !== '') params = params.set(k, String(v)); });
    return this.http.get<BoardColumn[]>('/api/demands/board', { params });
  }

  get(id: string): Observable<DemandDetail> { return this.http.get<DemandDetail>(`/api/demands/${id}`); }

  create(fields: Record<string, string | null>, source = 'PORTAL'): Observable<DemandDetail> {
    return this.http.post<DemandDetail>('/api/demands', { fields, source });
  }

  updateDraft(id: string, fields: Record<string, string | null>): Observable<DemandDetail> {
    return this.http.put<DemandDetail>(`/api/demands/${id}`, { fields });
  }

  discard(id: string): Observable<void> { return this.http.delete<void>(`/api/demands/${id}`); }

  validation(id: string): Observable<{ ready: boolean; fieldErrors: Record<string, string> }> {
    return this.http.get<{ ready: boolean; fieldErrors: Record<string, string> }>(`/api/demands/${id}/validation`);
  }

  submit(id: string): Observable<DemandDetail> { return this.http.post<DemandDetail>(`/api/demands/${id}/submit`, {}); }

  staffUpdate(id: string, fields: Record<string, string | null>, reason: string): Observable<DemandDetail> {
    return this.http.patch<DemandDetail>(`/api/demands/${id}`, { fields, reason });
  }

  assignOwner(id: string, ownerId: string | null): Observable<DemandDetail> {
    return this.http.put<DemandDetail>(`/api/demands/${id}/owner`, { ownerId });
  }

  transition(id: string, action: string, reason?: string): Observable<DemandDetail> {
    return this.http.post<DemandDetail>(`/api/demands/${id}/transitions`, { action, reason });
  }

  requestInformation(id: string, question: string): Observable<InformationRequest> {
    return this.http.post<InformationRequest>(`/api/demands/${id}/request-information`, { question });
  }

  informationRequests(id: string): Observable<InformationRequest[]> {
    return this.http.get<InformationRequest[]>(`/api/demands/${id}/information-requests`);
  }

  answer(id: string, requestId: string, response: string): Observable<InformationRequest> {
    return this.http.post<InformationRequest>(`/api/demands/${id}/information-requests/${requestId}/answer`, { response });
  }

  approvals(id: string): Observable<ApprovalView[]> { return this.http.get<ApprovalView[]>(`/api/demands/${id}/approvals`); }

  comments(id: string): Observable<Comment[]> { return this.http.get<Comment[]>(`/api/demands/${id}/comments`); }

  addComment(id: string, body: string, visibility: 'PUBLIC' | 'INTERNAL'): Observable<Comment> {
    return this.http.post<Comment>(`/api/demands/${id}/comments`, { body, visibility });
  }

  audit(id: string): Observable<AuditLog[]> { return this.http.get<AuditLog[]>(`/api/demands/${id}/audit`); }

  documents(id: string): Observable<DocumentView[]> { return this.http.get<DocumentView[]>(`/api/demands/${id}/documents`); }

  upload(id: string, file: File, kind: string): Observable<DocumentView> {
    const form = new FormData();
    form.append('file', file);
    form.append('kind', kind);
    return this.http.post<DocumentView>(`/api/demands/${id}/documents`, form);
  }

  download(documentId: string): Observable<Blob> {
    return this.http.get(`/api/documents/${documentId}/download`, { responseType: 'blob' });
  }

  pendingApprovals(): Observable<PendingApproval[]> { return this.http.get<PendingApproval[]>('/api/approvals/pending'); }

  decideApproval(approvalId: string, approve: boolean, comment?: string): Observable<ApprovalView> {
    return this.http.post<ApprovalView>(`/api/approvals/${approvalId}/decision`, { approve, comment });
  }
}
