import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { DashboardSummary } from './models';

@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_CONFIG).apiUrl}/api/v1/dashboard`;

  /** Resumo com a série diária separada no fuso informado (IANA, ex.: America/Sao_Paulo). */
  summary(days: number, timeZone: string): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.base}/summary`, {
      params: { days, timeZone },
    });
  }
}
