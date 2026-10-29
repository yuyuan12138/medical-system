import React, { useEffect, useState, useCallback } from 'react';
import { intakeApi, ReferralDto, ReferralDetailsDto, ReferralTrackingDto } from '../api/intakeApi';

const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  AWAITING_APPROVAL: '待审核',
  AWAITING_TRIAGE: '待分诊',
  WAITING_FOR_SCHEDULING: '待排期',
  WAITING_FOR_APPOINTMENT: '待就诊',
  AWAITING_FEEDBACK_APPROVAL: '待反馈确认',
  NEEDS_REASSIGNMENT: '需重新分配',
  REJECTED: '已驳回',
  RECALLED: '已撤回',
  CLOSED: '已归档',
  ERROR: '异常',
};

const RISK_LABELS: Record<string, string> = { HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' };
const RISK_STYLES: Record<string, string> = {
  HIGH: 'bg-[var(--md-sys-color-error-container)] text-[var(--md-sys-color-on-error-container)]',
  MEDIUM: 'bg-[var(--md-sys-color-tertiary-container)] text-[var(--md-sys-color-on-tertiary-container)]',
  LOW: 'bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)]',
};

function formatDate(s?: string | null): string {
  if (!s) return '—';
  const d = new Date(s);
  return isNaN(d.getTime()) ? s : d.toLocaleString('zh-CN', { dateStyle: 'medium', timeStyle: 'short' });
}

export const ReferralsPage: React.FC = () => {
  const [referrals, setReferrals] = useState<ReferralDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [details, setDetails] = useState<ReferralDetailsDto | null>(null);
  const [tracking, setTracking] = useState<ReferralTrackingDto | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setReferrals(await intakeApi.getReferrals());
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const openDetails = async (id: string) => {
    if (selectedId === id) { setSelectedId(null); return; }
    setSelectedId(id);
    setDetails(null);
    setTracking(null);
    setDetailLoading(true);
    try {
      const [d, t] = await Promise.all([
        intakeApi.getReferralDetails(id),
        intakeApi.getReferralTracking(id).catch(() => null),
      ]);
      setDetails(d);
      setTracking(t);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载详情失败');
    } finally {
      setDetailLoading(false);
    }
  };

  const selected = referrals.find(r => r.id === selectedId);

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold text-[var(--md-sys-color-on-surface)]">转诊进度</h2>
        <button className="text-sm text-[var(--md-sys-color-primary)]" onClick={load}>刷新</button>
      </div>

      {error && (
        <div className="p-3 rounded-xl bg-[var(--md-sys-color-error-container)] text-[var(--md-sys-color-on-error-container)] text-sm">
          {error}
        </div>
      )}

      {loading ? (
        <div className="py-10 text-center text-sm text-[var(--md-sys-color-on-surface-variant)]">加载中…</div>
      ) : referrals.length === 0 ? (
        <div className="py-10 text-center text-sm text-[var(--md-sys-color-on-surface-variant)]">暂无转诊记录</div>
      ) : (
        referrals.map(r => (
          <div key={r.id} className="rounded-2xl bg-[var(--md-sys-color-surface-container)] overflow-hidden">
            <button className="w-full text-left p-4" onClick={() => openDetails(r.id)}>
              <div className="flex items-center justify-between gap-2">
                <span className="font-semibold text-[var(--md-sys-color-on-surface)] truncate">{r.title}</span>
                <span className={`shrink-0 px-2 py-0.5 rounded-full text-xs ${RISK_STYLES[r.riskLevel] || ''}`}>
                  {RISK_LABELS[r.riskLevel] || r.riskLevel}
                </span>
              </div>
              <div className="mt-1 flex items-center justify-between text-xs text-[var(--md-sys-color-on-surface-variant)]">
                <span>{r.studentName}（{r.studentNumber}）</span>
                <span>{formatDate(r.date)}</span>
              </div>
              <div className="mt-2 inline-block px-2 py-0.5 rounded-full text-xs bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)]">
                {STATUS_LABELS[r.status] || r.status}
              </div>
            </button>

            {selectedId === r.id && (
              <div className="px-4 pb-4 border-t border-[var(--md-sys-color-outline-variant)]/50 pt-3 text-sm">
                {detailLoading && <div className="text-[var(--md-sys-color-on-surface-variant)]">加载详情…</div>}
                {details && (
                  <>
                    <div className="font-semibold mb-1">医生诊断</div>
                    <p className="mb-3 text-[var(--md-sys-color-on-surface-variant)]">
                      {details.triageInfo?.scidDiagnosis || '暂无诊断信息'}
                    </p>
                    <div className="font-semibold mb-1">当前状态</div>
                    <p className="mb-3 text-[var(--md-sys-color-on-surface-variant)]">
                      {STATUS_LABELS[selected?.status || ''] || selected?.status}
                    </p>
                    {tracking?.destination && (
                      <>
                        <div className="font-semibold mb-1">就诊安排</div>
                        <p className="text-[var(--md-sys-color-on-surface-variant)]">
                          {tracking.destination.hospital} · {tracking.destination.department}
                          {tracking.destination.doctor ? ` · ${tracking.destination.doctor}` : ''}
                          {tracking.destination.appointmentTime ? ` · ${formatDate(tracking.destination.appointmentTime)}` : ''}
                        </p>
                      </>
                    )}
                  </>
                )}
              </div>
            )}
          </div>
        ))
      )}
    </div>
  );
};
