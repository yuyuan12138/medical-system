import * as React from 'react';
import { RISK_FACTOR_LABELS, TEST_NAME_LABELS } from '../../config/referralConstants';
import { DetailsSection } from '../common/DetailsPanel';
import { PsychometricTable } from '../common/PsychometricTable';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Radar,
  RadarChart,
  PolarGrid,
  PolarAngleAxis
} from 'recharts';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../contexts/AuthContext';

interface PsychometricsTabContentProps {
  student: any;
  canEditDiagnosis?: boolean;
  onDiagnosisUpdated?: () => void;
}

const MemoizedLineChart = React.memo(({ scores }: { scores: any[] }) => (
  <ResponsiveContainer width="100%" height="100%" debounce={50}>
    <LineChart data={scores} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
      <CartesianGrid strokeDasharray="3 3" stroke="var(--md-sys-color-outline-variant)" vertical={false} />
      <XAxis
        dataKey="date"
        stroke="var(--md-sys-color-on-surface-variant)"
        fontSize={10}
        tickLine={false}
        axisLine={false}
      />
      <YAxis
        stroke="var(--md-sys-color-on-surface-variant)"
        fontSize={10}
        tickLine={false}
        axisLine={false}
        domain={[0, 100]}
        width={25}
      />
      <Tooltip
        contentStyle={{
          backgroundColor: 'var(--md-sys-color-surface-container-high)',
          border: 'none',
          borderRadius: '8px',
          fontSize: '12px',
          color: 'var(--md-sys-color-on-surface)'
        }}
      />
      <Line
        type="monotone"
        dataKey="value"
        stroke="var(--md-sys-color-primary)"
        strokeWidth={2}
        dot={{ fill: 'var(--md-sys-color-primary)', r: 4 }}
        activeDot={{ r: 6, stroke: 'var(--md-sys-color-surface)', strokeWidth: 2 }}
      />
    </LineChart>
  </ResponsiveContainer>
));

const MemoizedRadarChart = React.memo(({ radarData }: { radarData: any[] }) => (
  <RadarChart width={320} height={250} cx="50%" cy="50%" outerRadius="80%" data={radarData}>
    <PolarGrid stroke="var(--md-sys-color-outline-variant)" />
    <PolarAngleAxis
      dataKey="subject"
      tick={{ fill: 'var(--md-sys-color-on-surface-variant)', fontSize: 10 }}
    />
    <Radar
      name="Student"
      dataKey="A"
      stroke="var(--md-sys-color-secondary)"
      fill="var(--md-sys-color-secondary)"
      fillOpacity={0.5}
      isAnimationActive={false}
    />
  </RadarChart>
));


export function PsychometricsTabContent({ student, canEditDiagnosis, onDiagnosisUpdated }: PsychometricsTabContentProps) {
  const { session } = useAuth();
  const { data, isLoading: loading, error } = useQuery({
    queryKey: ['psychometrics', student?.id, session.token],
    queryFn: async () => {
      const res = await fetch(`${import.meta.env.BASE_URL}/api/students/${student?.id}/psychometrics`.replace('//api', '/api'), {
        headers: { 'Authorization': `Bearer ${session.token}` }
      });
      if (!res.ok) throw new Error('Failed to fetch psychometrics data');
      return res.json();
    },
    enabled: !!student?.id,
    staleTime: 1000 * 60 * 5, // Cache for 5 minutes
  });

  const [draftDiagnosis, setDraftDiagnosis] = React.useState<string | null>(null);
  const [diagnosisSaving, setDiagnosisSaving] = React.useState(false);
  const [diagnosisSaved, setDiagnosisSaved] = React.useState(false);
  const [diagnosisError, setDiagnosisError] = React.useState<string | null>(null);

  const queryClient = useQueryClient();

  const handleSaveDiagnosis = async () => {
    if (draftDiagnosis === null || !student?.id) return;
    setDiagnosisSaving(true);
    setDiagnosisError(null);
    setDiagnosisSaved(false);
    try {
      const res = await fetch(
        `${import.meta.env.BASE_URL}/api/students/${student.id}/health-profile`.replace('//api', '/api'),
        {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${session.token}` },
          body: JSON.stringify({ scidDiagnosis: draftDiagnosis.trim() })
        }
      );
      if (!res.ok) throw new Error(`保存失败 (${res.status})`);
      setDiagnosisSaved(true);
      await queryClient.invalidateQueries({ queryKey: ['psychometrics', student.id] });
      onDiagnosisUpdated?.();
    } catch (e: any) {
      setDiagnosisError(e?.message || '保存失败');
    } finally {
      setDiagnosisSaving(false);
    }
  };

  if (loading) {
    return <div className="py-8 text-center text-sm text-[var(--md-sys-color-on-surface-variant)] opacity-60">加载中...</div>;
  }

  if (error || !data) {
    return <div className="py-8 text-center text-sm text-[var(--md-sys-color-error)]">无法加载量表数据</div>;
  }

  return (
    <>
      {data.scidDiagnosis || (data.riskFlags && data.riskFlags.length > 0) ? (
        <DetailsSection title="临床实际情况" icon="psychology">
          <div className="flex flex-col gap-5">
            {(data.scidDiagnosis || canEditDiagnosis) && (
              <div className="flex flex-col gap-1.5 p-4 rounded-2xl bg-[var(--md-sys-color-surface-container-low)]">
                <span className="text-[12px] font-bold text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest opacity-70">SCID诊断</span>
                {canEditDiagnosis ? (
                  <div className="flex flex-col gap-2">
                    <textarea
                      className="w-full min-h-[72px] p-3 rounded-xl text-[14px] bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] outline-none focus:border-[var(--md-sys-color-primary)] resize-y"
                      placeholder="填写SCID诊断结论，保存后学生端与高危名单即时可见"
                      defaultValue={data.scidDiagnosis || ''}
                      onChange={e => setDraftDiagnosis(e.target.value)}
                    />
                    <div className="flex items-center gap-3">
                      <button
                        className="px-4 py-2 rounded-full bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] text-[13px] font-medium disabled:opacity-50"
                        disabled={diagnosisSaving}
                        onClick={handleSaveDiagnosis}
                      >
                        {diagnosisSaving ? '保存中…' : '保存诊断'}
                      </button>
                      {diagnosisSaved && <span className="text-[12px] text-[var(--md-sys-color-on-surface-variant)]">已保存</span>}
                      {diagnosisError && <span className="text-[12px] text-[var(--md-sys-color-error)]">{diagnosisError}</span>}
                    </div>
                  </div>
                ) : (
                  <span className="text-[16px] font-medium text-[var(--md-sys-color-on-surface)]">{data.scidDiagnosis}</span>
                )}
              </div>
            )}

            {data.riskFlags && data.riskFlags.length > 0 && (
              <div className="flex flex-col gap-3">
                <span className="text-[12px] font-bold text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest opacity-70 ml-1">严重风险标记</span>
                <div className="grid grid-cols-1 gap-2.5">
                  {data.riskFlags.map((flag: any, idx: number) => (
                    <div key={idx} className="flex items-center justify-between px-4 py-3 rounded-xl bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] border-opacity-50 transition-all hover:bg-[var(--md-sys-color-surface-container-low)]">
                      <span className="text-[14px] font-medium text-[var(--md-sys-color-on-surface)]">{RISK_FACTOR_LABELS[flag.label] || flag.label}</span>
                      <div className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-[12px] font-bold ${flag.value
                        ? 'bg-[var(--md-sys-color-error-container)] text-[var(--md-sys-color-on-error-container)]'
                        : 'bg-[var(--md-sys-color-surface-variant)] text-[var(--md-sys-color-on-surface-variant)] opacity-40'
                        }`}>
                        {flag.value ? '阳性 (+)' : '阴性 (-)'}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </DetailsSection>
      ) : null}

      {data.scores && data.scores.length > 0 && (
        <div className="flex flex-col gap-2">
          <h4 className="text-sm font-medium text-[var(--md-sys-color-on-surface-variant)] flex items-center gap-2">
            <span className="material-symbols-outlined text-sm">trending_up</span>
            分数趋势 (GAD-7)
          </h4>
          <div className="w-full h-48 bg-[var(--md-sys-color-surface-container-low)] rounded-2xl p-4 mt-2">
            <MemoizedLineChart scores={data.scores} />
          </div>
        </div>
      )}

      {data.radarData && data.radarData.length > 0 && (
        <div className="flex flex-col gap-2">
          <h4 className="text-sm font-medium text-[var(--md-sys-color-on-surface-variant)] flex items-center gap-2">
            <span className="material-symbols-outlined text-sm">hub</span>
            症状分布
          </h4>
          <div className="w-full h-64 flex justify-center mt-2 overflow-visible">
            <MemoizedRadarChart radarData={data.radarData} />
          </div>
        </div>
      )}

      {data.tests && data.tests.length > 0 ? (
        <PsychometricTable scores={data.tests.map(t => ({ ...t, name: TEST_NAME_LABELS[t.name] || t.name }))} />
      ) : (
        <div className="py-8 text-center text-sm text-[var(--md-sys-color-on-surface-variant)] opacity-60">暂无量表数据</div>
      )}
    </>
  );
}
